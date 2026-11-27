// ConviTEC — página de una sede. Habla con la API REST del mismo servidor (ServidorRest.java).
'use strict';

const estado = {
  info: null,        // GET /api/sede
  catalogo: [],      // GET /api/catalogo
  lineas: [],        // líneas del pedido que se está armando: { codigo, nombre, cantidad, precio }
};

// ---------- Utilidades ----------

/** Llama a la API. Si responde error, lanza un Error con el mensaje de { "error": "..." }. */
async function api(metodo, ruta, cuerpo) {
  const opciones = { method: metodo, headers: {} };
  if (cuerpo !== undefined) {
    opciones.headers['Content-Type'] = 'application/json';
    opciones.body = JSON.stringify(cuerpo);
  }
  let respuesta;
  try {
    respuesta = await fetch('/api' + ruta, opciones);
  } catch (e) {
    throw new Error('No hay conexión con el servidor de la sede');
  }
  const texto = await respuesta.text();
  const datos = texto ? JSON.parse(texto) : null;
  if (!respuesta.ok) {
    throw new Error((datos && datos.error) || 'Error ' + respuesta.status);
  }
  return datos;
}

const formatoColones = new Intl.NumberFormat('es-CR', { style: 'currency', currency: 'CRC', maximumFractionDigits: 0 });
const colones = (n) => formatoColones.format(n || 0);
const numero = (n, dec = 0) => (n || 0).toLocaleString('es-CR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
const fecha = (iso) => (iso ? iso.replace('T', ' ').slice(0, 16) : '');

/** Escapa texto antes de meterlo en HTML. */
function esc(valor) {
  return String(valor ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

const $ = (id) => document.getElementById(id);

/** Arma una tabla: columnas = [{ titulo, clase }], filas = arreglo de arreglos de celdas HTML. */
function tabla(id, columnas, filas, textoVacio) {
  const cabecera = '<thead><tr>' + columnas.map((c) => `<th class="${c.clase || ''}">${esc(c.titulo)}</th>`).join('') + '</tr></thead>';
  let cuerpo;
  if (filas.length === 0) {
    cuerpo = `<tr><td class="vacio" colspan="${columnas.length}">${esc(textoVacio)}</td></tr>`;
  } else {
    cuerpo = filas.map((fila) => '<tr>' + fila.map((celda, i) => {
      const clase = typeof celda === 'object' && celda !== null ? celda.clase : columnas[i].clase;
      const html = typeof celda === 'object' && celda !== null ? celda.html : celda;
      return `<td class="${clase || ''}">${html}</td>`;
    }).join('') + '</tr>').join('');
  }
  $(id).innerHTML = cabecera + '<tbody>' + cuerpo + '</tbody>';
}

let temporizadorMensaje;
function avisar(texto, tipo = 'ok') {
  const caja = $('mensaje');
  caja.textContent = texto;
  caja.className = 'mensaje ' + tipo;
  caja.hidden = false;
  clearTimeout(temporizadorMensaje);
  temporizadorMensaje = setTimeout(() => { caja.hidden = true; }, 5000);
}

const etiqueta = (estadoPedido) => `<span class="etiqueta ${esc(estadoPedido)}">${esc((estadoPedido || '').replace('_', ' '))}</span>`;
const resumenLineas = (lineas) => (lineas || []).map((l) =>
  `${esc(l.cantidad)} × ${esc(l.nombre || l.codigo)}${l.sedeQueSuministra ? ` <span class="detalle">(${esc(l.sedeQueSuministra)})</span>` : ''}`).join('<br>');
const totalLineas = (lineas) => (lineas || []).reduce((suma, l) => suma + l.cantidad * l.precioUnitario, 0);

// ---------- Pestañas ----------

const cargadores = {
  catalogo: cargarCatalogo,
  inventario: cargarInventario,
  pedido: prepararPedido,
  pedidos: cargarPedidos,
  entregas: cargarEntregas,
  ventas: cargarVentas,
};

function abrirPestana(nombre) {
  document.querySelectorAll('#pestanas button').forEach((b) => b.classList.toggle('activa', b.dataset.tab === nombre));
  document.querySelectorAll('.tab').forEach((s) => s.classList.toggle('activa', s.id === 'tab-' + nombre));
  location.hash = nombre;
  ejecutar(cargadores[nombre]);
}

/** Corre una función async y muestra su error en la barra de mensajes. */
async function ejecutar(funcion) {
  try {
    await funcion();
  } catch (e) {
    avisar(e.message, 'error');
  }
}

// ---------- 1. Catálogo global ----------

async function cargarCatalogo() {
  estado.catalogo = await api('GET', '/catalogo');
  pintarCatalogo();
}

function pintarCatalogo() {
  const filtro = $('filtro-catalogo').value.trim().toLowerCase();
  const sedes = estado.info.sedes;
  const columnas = [{ titulo: 'Código' }, { titulo: 'Producto' }, { titulo: 'Categoría' }, { titulo: 'Precio', clase: 'num' }]
    .concat(sedes.map((s, i) => ({ titulo: s.nombre, clase: 'num' + (i === estado.info.indice ? ' local' : '') })))
    .concat([{ titulo: 'Total', clase: 'num' }]);
  const filas = estado.catalogo
    .filter((n) => !filtro || [n.producto.codigo, n.producto.nombre, n.producto.categoria].join(' ').toLowerCase().includes(filtro))
    .map((n) => {
      const total = n.existencias.reduce((a, b) => a + b, 0);
      return [esc(n.producto.codigo), `${esc(n.producto.nombre)}<div class="detalle">${esc(n.producto.descripcion)}</div>`,
        esc(n.producto.categoria), colones(n.producto.precio)]
        .concat(n.existencias.map((e, i) => ({ html: numero(e), clase: 'num' + (i === estado.info.indice ? ' local' : '') + (e === 0 ? ' cero' : '') })))
        .concat([{ html: `<strong>${numero(total)}</strong>`, clase: 'num' }]);
    });
  tabla('tabla-catalogo', columnas, filas, filtro ? 'Ningún producto coincide con la búsqueda' : 'No hay productos en el catálogo');
}

// ---------- 2. Inventario local ----------

async function cargarInventario() {
  const items = await api('GET', '/inventario');
  const columnas = [{ titulo: 'Código' }, { titulo: 'Producto' }, { titulo: 'Categoría' }, { titulo: 'Precio', clase: 'num' },
    { titulo: 'Existencia', clase: 'num' }, { titulo: 'Agregar unidades', clase: 'num' }];
  const filas = items.map((p) => [esc(p.codigo), esc(p.nombre), esc(p.categoria), colones(p.precio),
    { html: numero(p.existencia), clase: 'num' + (p.existencia === 0 ? ' cero' : '') },
    `<form class="agregar" data-codigo="${esc(p.codigo)}"><input type="number" min="1" value="10" aria-label="Unidades de ${esc(p.nombre)}"><button class="chico">Agregar</button></form>`]);
  tabla('tabla-inventario', columnas, filas, 'Esta sede no tiene productos');
}

$('tabla-inventario').addEventListener('submit', (evento) => {
  evento.preventDefault();
  const form = evento.target;
  const cantidad = parseInt(form.querySelector('input').value, 10);
  ejecutar(async () => {
    const r = await api('POST', '/inventario/' + encodeURIComponent(form.dataset.codigo), { cantidad });
    avisar(r.mensaje);
    await cargarInventario();
  });
});

// ---------- 3. Nuevo pedido ----------

async function prepararPedido() {
  estado.catalogo = await api('GET', '/catalogo');
  $('pedido-producto').innerHTML = estado.catalogo.map((n) => {
    const total = n.existencias.reduce((a, b) => a + b, 0);
    return `<option value="${esc(n.producto.codigo)}">${esc(n.producto.codigo)} · ${esc(n.producto.nombre)} — ${colones(n.producto.precio)} (${numero(total)} disp.)</option>`;
  }).join('');
  pintarLineas();
}

function pintarLineas() {
  const filas = estado.lineas.map((l, i) => [esc(l.codigo), esc(l.nombre), { html: numero(l.cantidad), clase: 'num' },
    colones(l.precio), { html: colones(l.cantidad * l.precio), clase: 'num' },
    `<button class="chico" data-quitar="${i}">Quitar</button>`]);
  const total = estado.lineas.reduce((s, l) => s + l.cantidad * l.precio, 0);
  if (filas.length > 0) {
    filas.push(['', '<strong>Total</strong>', '', '', { html: `<strong>${colones(total)}</strong>`, clase: 'num' }, '']);
  }
  tabla('tabla-lineas', [{ titulo: 'Código' }, { titulo: 'Producto' }, { titulo: 'Cantidad', clase: 'num' },
    { titulo: 'Precio' }, { titulo: 'Subtotal', clase: 'num' }, { titulo: '' }], filas, 'Agregue productos al pedido');
}

$('form-linea').addEventListener('submit', (evento) => {
  evento.preventDefault();
  const codigo = $('pedido-producto').value;
  const cantidad = parseInt($('pedido-cantidad').value, 10);
  const nodo = estado.catalogo.find((n) => n.producto.codigo === codigo);
  if (!nodo || !(cantidad > 0)) {
    avisar('Elija un producto y una cantidad mayor que 0', 'error');
    return;
  }
  const existente = estado.lineas.find((l) => l.codigo === codigo);
  if (existente) {
    existente.cantidad += cantidad;
  } else {
    estado.lineas.push({ codigo, nombre: nodo.producto.nombre, cantidad, precio: nodo.producto.precio });
  }
  $('pedido-cantidad').value = 1;
  pintarLineas();
});

$('tabla-lineas').addEventListener('click', (evento) => {
  const indice = evento.target.dataset.quitar;
  if (indice !== undefined) {
    estado.lineas.splice(Number(indice), 1);
    pintarLineas();
  }
});

$('pedido-mostrador').addEventListener('change', () => {
  $('pedido-destino').disabled = $('pedido-mostrador').checked;
});

$('form-pedido').addEventListener('submit', (evento) => {
  evento.preventDefault();
  if (estado.lineas.length === 0) {
    avisar('El pedido no tiene productos', 'error');
    return;
  }
  const boton = evento.submitter;
  boton.disabled = true;
  ejecutar(async () => {
    try {
      const mostrador = $('pedido-mostrador').checked;
      const r = await api('POST', '/pedidos', {
        destino: mostrador ? null : $('pedido-destino').value,
        mostrador,
        items: estado.lineas.map((l) => ({ codigo: l.codigo, cantidad: l.cantidad })),
      });
      mostrarResultado(r);
      if (r.ok) {
        estado.lineas = [];
        await prepararPedido();
      }
    } finally {
      boton.disabled = false;
    }
  });
});

function mostrarResultado(r) {
  const p = r.pedido;
  const detalle = p && r.ok
    ? `<p>Destino: <strong>${esc(p.mostrador ? 'mostrador' : p.destino)}</strong> · Estado: ${etiqueta(p.estado)} · Total ${colones(totalLineas(p.lineas))}</p><p>${resumenLineas(p.lineas)}</p>`
    : '<p>Se canceló el pedido completo y se liberaron las reservas.</p>';
  $('resultado-pedido').innerHTML = `<div class="resultado ${r.ok ? 'ok' : 'error'}"><strong>${r.ok ? '✔ ' : '✖ Pedido rechazado: '}${esc(r.mensaje)}</strong>${detalle}</div>`;
}

// ---------- 4. Pedidos ----------

async function cargarPedidos() {
  const [pendientes, historial] = await Promise.all([api('GET', '/pedidos/pendientes'), api('GET', '/pedidos')]);
  const filaPedido = (p) => [esc(p.id), fecha(p.fechaCreacion), esc(p.mostrador ? 'Mostrador' : p.destino), resumenLineas(p.lineas),
    { html: colones(totalLineas(p.lineas)), clase: 'num' }, etiqueta(p.estado) + (p.motivoCancelacion ? `<div class="detalle">${esc(p.motivoCancelacion)}</div>` : '')];
  const columnas = [{ titulo: 'Pedido' }, { titulo: 'Fecha' }, { titulo: 'Destino' }, { titulo: 'Productos' }, { titulo: 'Monto', clase: 'num' }, { titulo: 'Estado' }];
  tabla('tabla-pendientes', columnas, pendientes.map(filaPedido), 'No hay pedidos pendientes de entrega');
  tabla('tabla-historial', columnas, historial.slice().reverse().map(filaPedido), 'Todavía no se han registrado pedidos');
}

// ---------- 5. Entregas ----------

async function cargarEntregas() {
  const recorridos = await api('GET', '/entregas');
  pintarRecorridos(recorridos);
}

function pintarRecorridos(recorridos) {
  if (recorridos.length === 0) {
    $('recorridos').innerHTML = '<p class="ayuda">Todavía no hay recorridos. Registre pedidos con destino y calcule el siguiente punto.</p>';
    return;
  }
  $('recorridos').innerHTML = recorridos.slice().reverse().map((r) => {
    const pasos = (r.pasos || []).map((p, i) => `<tr><td class="num">${i + 1}</td><td>${esc(p.desde)} → <strong>${esc(p.hasta)}</strong>
      <div class="camino">${esc((p.camino || []).join(' → '))}</div></td><td class="num">${numero(p.minutos)} min</td>
      <td class="num">${numero(p.tiempoAcumulado)} min</td><td>${esc((p.pedidosEntregados || []).join(', '))}</td></tr>`).join('');
    return `<div class="recorrido"><h3><span>Salida ${esc(r.horaSalida)} desde ${esc(r.sede)}</span>
      <span>${r.terminado ? 'Terminado' : 'En curso'} · Tiempo total: ${numero(r.tiempoTotal)} min</span></h3>
      <div class="tabla-envoltura"><table><thead><tr><th class="num">#</th><th>Tramo</th><th class="num">Minutos</th><th class="num">Acumulado</th><th>Pedidos entregados</th></tr></thead>
      <tbody>${pasos || '<tr><td class="vacio" colspan="5">Sin entregas todavía</td></tr>'}</tbody></table></div></div>`;
  }).join('');
}

$('btn-siguiente').addEventListener('click', () => {
  const boton = $('btn-siguiente');
  boton.disabled = true;
  ejecutar(async () => {
    try {
      const r = await api('POST', '/entregas/siguiente');
      const p = r.paso;
      $('ultimo-paso').innerHTML = p
        ? `<div class="resultado ok"><strong>${esc(r.mensaje)}</strong><p>Camino: ${esc((p.camino || []).join(' → '))} · Pedidos: ${esc((p.pedidosEntregados || []).join(', '))} · Acumulado: ${numero(p.tiempoAcumulado)} min</p></div>`
        : `<div class="resultado">${esc(r.mensaje)}</div>`;
      await cargarEntregas();
    } finally {
      boton.disabled = false;
    }
  });
});

// ---------- 6. Ventas y estadísticas ----------

async function cargarVentas() {
  const [stats, ventas] = await Promise.all([api('GET', '/estadisticas'), api('GET', '/ventas')]);
  const e = stats.estrella;
  $('tarjetas').innerHTML = `
    <div class="tarjeta estrella"><div class="titulo">⭐ Producto estrella</div>
      <div class="valor">${e ? esc(e.nombre) : '—'}</div>
      <div class="sub">${e ? `${colones(e.ingresos)} en ingresos · ${numero(e.unidades)} unidades` : 'Sin ventas todavía'}</div></div>
    <div class="tarjeta"><div class="titulo">Ingresos de la sede</div><div class="valor">${colones(stats.ingresosTotales)}</div>
      <div class="sub">${numero(stats.cantidadVentas)} ventas</div></div>
    <div class="tarjeta"><div class="titulo">Peor pedido</div>
      <div class="valor">${stats.peoresPedidos.length ? esc(stats.peoresPedidos[0].pedidoId) : '—'}</div>
      <div class="sub">${stats.peoresPedidos.length ? `${numero(stats.peoresPedidos[0].rendimiento, 1)} ₡/min` : 'Sin entregas todavía'}</div></div>`;

  tabla('tabla-mas-vendidos', [{ titulo: '#', clase: 'num' }, { titulo: 'Producto' }, { titulo: 'Unidades', clase: 'num' }, { titulo: 'Ingresos', clase: 'num' }],
    stats.masVendidos.map((p, i) => [numero(i + 1), `${esc(p.nombre)} <span class="detalle">${esc(p.codigo)}</span>`, numero(p.unidades), colones(p.ingresos)]),
    'Sin ventas todavía');

  tabla('tabla-peores', [{ titulo: 'Pedido' }, { titulo: 'Monto', clase: 'num' }, { titulo: 'Tiempo', clase: 'num' }, { titulo: '₡/min', clase: 'num' }],
    stats.peoresPedidos.map((p) => [esc(p.pedidoId), colones(p.montoTotal), `${numero(p.tiempoEntrega)} min`, numero(p.rendimiento, 1)]),
    'Ningún pedido a domicilio se ha entregado todavía');

  tabla('tabla-ventas', [{ titulo: 'Pedido' }, { titulo: 'Fecha' }, { titulo: 'Productos (sede que suministra)' }, { titulo: 'Tipo' },
    { titulo: 'Tiempo', clase: 'num' }, { titulo: 'Monto', clase: 'num' }],
    ventas.slice().reverse().map((v) => [esc(v.pedidoId), fecha(v.fecha),
      resumenLineas(v.lineas) + (v.lineas || []).map((l) => `<div class="detalle">${colones(l.precioUnitario)} c/u</div>`).join(''),
      v.mostrador ? 'Mostrador' : 'Entrega', v.mostrador ? '—' : (v.tiempoEntrega ? `${numero(v.tiempoEntrega)} min` : 'Pendiente'),
      colones(v.montoTotal)]),
    'Todavía no hay ventas');
}

// ---------- Arranque ----------

async function iniciar() {
  try {
    estado.info = await api('GET', '/sede');
  } catch (e) {
    $('sede').textContent = 'Sin conexión';
    avisar(e.message, 'error');
    return;
  }
  const info = estado.info;
  document.title = `ConviTEC · ${info.nombre}`;
  $('sede').textContent = `Sede ${info.nombre} (${info.codigo})`;
  $('hora').textContent = info.horaVirtual;
  $('demo').hidden = !info.datosFalsos;
  $('pedido-destino').innerHTML = info.destinos.map((d) => `<option ${d === info.ubicacion ? 'selected' : ''}>${esc(d)}</option>`).join('');

  document.querySelectorAll('#pestanas button').forEach((b) => b.addEventListener('click', () => abrirPestana(b.dataset.tab)));
  $('filtro-catalogo').addEventListener('input', pintarCatalogo);

  // La hora virtual avanza en el servidor; se refresca cada 20 s.
  setInterval(async () => {
    try { $('hora').textContent = (await api('GET', '/sede')).horaVirtual; } catch (e) { /* se reintenta luego */ }
  }, 20000);

  const inicial = location.hash.slice(1);
  abrirPestana(cargadores[inicial] ? inicial : 'catalogo');
}

iniciar();
