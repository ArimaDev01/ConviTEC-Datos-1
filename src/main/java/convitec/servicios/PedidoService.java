package convitec.servicios;

import convitec.estructuras.Cola;
import convitec.estructuras.ListaEnlazada;
import convitec.mensajeria.CanalMensajes;
import convitec.modelo.EstadoPedido;
import convitec.modelo.LineaPedido;
import convitec.modelo.Mensaje;
import convitec.modelo.NodoCatalogo;
import convitec.modelo.Pedido;
import convitec.modelo.ResultadoPedido;
import convitec.modelo.Sede;
import convitec.modelo.TipoMensaje;
import convitec.modelo.Venta;
import convitec.persistencia.AlmacenJson;
import convitec.persistencia.Config;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Registro de pedidos de una sede.
 *
 * Flujo de {@link #registrar}:
 * <ol>
 *   <li><b>Validar</b>: hay productos, cantidades mayores que 0, destino si no es de mostrador.
 *       Las líneas repetidas del mismo producto se suman.</li>
 *   <li><b>Asignar sedes</b>: cada producto se toma primero de esta sede y lo que falte de las
 *       otras, empezando por la que más tiene (según la copia del inventario global).</li>
 *   <li><b>Reservar</b>: lo local se aparta directo; a cada otra sede se le envía RESERVAR y se
 *       esperan sus RESPUESTA, como máximo 5 s. Si una rechaza o no contesta, se cancela.</li>
 *   <li><b>Verificar la ruta</b> con el Dijkstra de P2 (las ventas de mostrador no la necesitan).</li>
 *   <li><b>Confirmar</b> (CONFIRMAR a las sedes participantes) o <b>cancelar</b> (LIBERAR).</li>
 * </ol>
 * Un pedido confirmado queda PENDIENTE, entra a la cola de entregas y genera su venta.
 * Una venta de mostrador queda ENTREGADO con tiempo de entrega 0 y no entra a la cola.
 *
 * Los pedidos se registran de uno en uno (un pedido a la vez por sede), así dos pedidos
 * no compiten por las mismas unidades locales a medio reservar.
 *
 * Archivos en data/SEDE/: pedidos.json y ventas.json. La cola de pendientes no se guarda
 * aparte: al arrancar se reconstruye con los pedidos PENDIENTE o EN_RUTA, en el mismo orden.
 */
public class PedidoService {
    static final String ARCHIVO_PEDIDOS = "pedidos.json";
    static final String ARCHIVO_VENTAS = "ventas.json";
    /** Tiempo máximo de espera de las respuestas de las otras sedes. */
    public static final long ESPERA_RESERVAS_MS = 5000;

    private final InventarioService inventario;
    private final CanalMensajes canal;
    private final VerificadorRutas rutas;
    private final AlmacenJson almacen;
    private final long esperaMs;
    private final String codigoLocal;

    private final ListaEnlazada<Pedido> pedidos = new ListaEnlazada<>();
    private final Cola<Pedido> pendientes = new Cola<>();
    private final ListaEnlazada<Venta> ventas = new ListaEnlazada<>();
    private final ListaEnlazada<EsperaReservas> esperas = new ListaEnlazada<>();
    /** Candado para registrar los pedidos de uno en uno. */
    private final Object registrando = new Object();
    private int consecutivo;

    /**
     * @param almacen  null para no guardar archivos (pruebas)
     * @param esperaMs cuánto esperar las respuestas de RESERVAR (normalmente {@link #ESPERA_RESERVAS_MS})
     */
    public PedidoService(InventarioService inventario, CanalMensajes canal, VerificadorRutas rutas,
                         AlmacenJson almacen, long esperaMs) {
        if (inventario == null || canal == null || rutas == null) {
            throw new IllegalArgumentException("PedidoService necesita inventario, canal y verificador de rutas");
        }
        this.inventario = inventario;
        this.canal = canal;
        this.rutas = rutas;
        this.almacen = almacen;
        this.esperaMs = esperaMs;
        this.codigoLocal = inventario.getSedeLocal().getCodigo();
        cargar();
        canal.alRecibir(this::procesar);
    }

    public static PedidoService crear(Config config, InventarioService inventario, CanalMensajes canal,
                                      VerificadorRutas rutas) {
        return new PedidoService(inventario, canal, rutas, new AlmacenJson(config.getCarpetaDatosSede()),
                ESPERA_RESERVAS_MS);
    }

    /**
     * Se llama después de conectar el canal. Si la sede se apagó a media reserva, esos pedidos
     * quedaron RESERVANDO: se cancelan y se liberan sus reservas en todas las sedes.
     */
    public void iniciar() {
        synchronized (registrando) {
            for (Pedido pedido : pedidos()) {
                if (pedido.getEstado() == EstadoPedido.RESERVANDO) {
                    cancelar(pedido, "La sede se reinició mientras se reservaban las unidades");
                }
            }
            guardar();
        }
    }

    // ---------- Registrar ----------

    /**
     * Registra un pedido. Una entrada inválida lanza IllegalArgumentException; un pedido que no
     * se puede completar (producto inexistente, sin existencias, sede sin responder, destino
     * inalcanzable) queda CANCELADO en el historial y se devuelve rechazado con el motivo.
     */
    public ResultadoPedido registrar(String destino, boolean mostrador, LineaPedido[] items) {
        validarEntrada(destino, mostrador, items);
        synchronized (registrando) {
            Sede local = inventario.getSedeLocal();
            String lugar = mostrador ? local.getUbicacion() : destino.trim();
            String fecha = ahora();
            Pedido pedido = new Pedido(nuevoId(), codigoLocal, fecha, lugar, mostrador, sumarRepetidas(items));
            synchronized (this) {
                pedidos.agregar(pedido);
            }

            String motivo = completarProductos(pedido);
            if (motivo == null) {
                motivo = asignarSedes(pedido);
            }
            if (motivo != null) {
                // Todavía no se reservó nada: basta con cancelar.
                pedido.cancelar(motivo);
                guardar();
                return ResultadoPedido.rechazado(motivo, pedido);
            }

            motivo = reservar(pedido);
            if (motivo == null && !mostrador && !rutas.esAlcanzable(lugar)) {
                motivo = "No hay ruta desde " + local.getUbicacion() + " hasta " + lugar
                        + "; se liberaron las unidades reservadas";
            }
            if (motivo != null) {
                cancelar(pedido, motivo);
                guardar();
                return ResultadoPedido.rechazado(motivo, pedido);
            }

            confirmar(pedido, fecha);
            guardar();
            return ResultadoPedido.aceptado(pedido);
        }
    }

    private static void validarEntrada(String destino, boolean mostrador, LineaPedido[] items) {
        if (items == null || items.length == 0) {
            throw new IllegalArgumentException("El pedido debe tener al menos un producto");
        }
        for (int i = 0; i < items.length; i++) {
            LineaPedido item = items[i];
            if (item == null || item.getCodigo() == null || item.getCodigo().isBlank()) {
                throw new IllegalArgumentException("La línea " + (i + 1) + " no indica el producto");
            }
            if (item.getCantidad() <= 0) {
                throw new IllegalArgumentException("La cantidad de " + item.getCodigo() + " debe ser mayor que 0");
            }
        }
        if (!mostrador && (destino == null || destino.isBlank())) {
            throw new IllegalArgumentException("Indique el punto de entrega o marque venta de mostrador");
        }
    }

    /** Une las líneas del mismo producto sumando sus cantidades, en el orden en que aparecen. */
    static LineaPedido[] sumarRepetidas(LineaPedido[] items) {
        ListaEnlazada<LineaPedido> sumadas = new ListaEnlazada<>();
        for (LineaPedido item : items) {
            String codigo = item.getCodigo().trim();
            LineaPedido existente = null;
            for (LineaPedido linea : sumadas.toArray(new LineaPedido[0])) {
                if (linea.getCodigo().equals(codigo)) {
                    existente = linea;
                    break;
                }
            }
            if (existente == null) {
                sumadas.agregar(new LineaPedido(codigo, item.getCantidad()));
            } else {
                existente.setCantidad(existente.getCantidad() + item.getCantidad());
            }
        }
        return sumadas.toArray(new LineaPedido[0]);
    }

    /** Llena nombre y precio de cada línea; devuelve el motivo si algún producto no existe. */
    private String completarProductos(Pedido pedido) {
        for (LineaPedido linea : pedido.getLineas()) {
            NodoCatalogo nodo = inventario.buscar(linea.getCodigo());
            if (nodo == null) {
                return "No existe el producto " + linea.getCodigo();
            }
            linea.setNombre(nodo.getProducto().getNombre());
            linea.setPrecioUnitario(nodo.getProducto().getPrecio());
        }
        return null;
    }

    /**
     * Parte cada línea entre las sedes: primero la local y después las demás, de la que más
     * tiene a la que menos. Si entre todas no alcanza, devuelve el motivo.
     */
    private String asignarSedes(Pedido pedido) {
        Sede[] sedes = inventario.getSedes();
        int indiceLocal = inventario.getIndiceLocal();
        ListaEnlazada<LineaPedido> asignadas = new ListaEnlazada<>();
        for (LineaPedido linea : pedido.getLineas()) {
            int[] existencias = inventario.buscar(linea.getCodigo()).getExistencias();
            int total = 0;
            for (int cantidad : existencias) {
                total += cantidad;
            }
            if (total < linea.getCantidad()) {
                return "No hay suficiente " + linea.getNombre() + ": se piden " + linea.getCantidad()
                        + " y hay " + total + " en todas las sedes";
            }
            int falta = linea.getCantidad();
            int indice = indiceLocal;
            while (falta > 0) {
                int tomar = Math.min(falta, existencias[indice]);
                if (tomar > 0) {
                    asignadas.agregar(new LineaPedido(linea.getCodigo(), linea.getNombre(), tomar,
                            linea.getPrecioUnitario(), sedes[indice].getCodigo()));
                    existencias[indice] -= tomar;
                    falta -= tomar;
                }
                indice = sedeConMasExistencia(existencias);
            }
        }
        pedido.setLineas(asignadas.toArray(new LineaPedido[0]));
        return null;
    }

    private static int sedeConMasExistencia(int[] existencias) {
        int mejor = 0;
        for (int i = 1; i < existencias.length; i++) {
            if (existencias[i] > existencias[mejor]) {
                mejor = i;
            }
        }
        return mejor;
    }

    /**
     * Aparta las unidades: lo local directo y lo de otras sedes con RESERVAR, esperando las
     * respuestas. Devuelve null si todo quedó reservado o el motivo del fallo (sin liberar:
     * eso lo hace quien llama, con {@link #cancelar}).
     */
    private String reservar(Pedido pedido) {
        pedido.setEstado(EstadoPedido.RESERVANDO);
        guardar(); // si la sede se apaga aquí, al arrancar se libera (ver iniciar)
        EsperaReservas espera = new EsperaReservas(pedido.getId());
        for (LineaPedido linea : pedido.getLineas()) {
            if (linea.getSedeQueSuministra().equals(codigoLocal)) {
                String motivo = inventario.reservar(pedido.getId(), codigoLocal, linea.getCodigo(), linea.getCantidad());
                if (motivo != null) {
                    return motivo;
                }
            } else {
                espera.agregar(linea.getSedeQueSuministra(), linea.getCodigo());
            }
        }
        if (espera.sedesSinRespuesta().isEmpty()) {
            return null; // todo era local
        }

        synchronized (esperas) {
            esperas.agregar(espera); // antes de enviar, por si la respuesta llega muy rápido
        }
        try {
            for (LineaPedido linea : pedido.getLineas()) {
                if (!linea.getSedeQueSuministra().equals(codigoLocal)) {
                    Mensaje m = new Mensaje(TipoMensaje.RESERVAR, codigoLocal, linea.getSedeQueSuministra());
                    m.setPedidoId(pedido.getId());
                    m.setCodigo(linea.getCodigo());
                    m.setCantidad(linea.getCantidad());
                    canal.enviar(m);
                }
            }
            boolean todasSi = espera.esperar(esperaMs);
            if (todasSi) {
                return null;
            }
            if (espera.getRechazo() != null) {
                return espera.getRechazo();
            }
            return "La sede " + espera.sedesSinRespuesta() + " no respondió en " + (esperaMs / 1000.0)
                    + " s (puede estar apagada); se liberaron las unidades reservadas";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Se interrumpió la reserva del pedido";
        } catch (RuntimeException e) {
            return "No se pudo hablar con las otras sedes: " + e.getMessage();
        } finally {
            synchronized (esperas) {
                esperas.eliminar(espera);
            }
        }
    }

    /** Confirma las reservas en todas las sedes participantes, crea la venta y encola el pedido. */
    private void confirmar(Pedido pedido, String fecha) {
        inventario.confirmar(pedido.getId());
        avisarParticipantes(pedido, TipoMensaje.CONFIRMAR);
        Venta venta = new Venta(pedido, fecha);
        synchronized (this) {
            ventas.agregar(venta);
            if (pedido.isMostrador()) {
                pedido.setEstado(EstadoPedido.ENTREGADO);
                pedido.setTiempoTraslado(0);
                venta.setTiempoEntrega(0);
            } else {
                pedido.setEstado(EstadoPedido.PENDIENTE);
            }
        }
        if (!pedido.isMostrador()) {
            // Fuera del candado de this: DespachoService toma primero la cola y después llama a
            // marcarEntregado; tomar los dos candados en orden contrario podría trabar las dos sedes.
            synchronized (pendientes) {
                pendientes.encolar(pedido);
            }
        }
    }

    /** Cancela el pedido y devuelve lo reservado: lo local aquí y lo demás con LIBERAR. */
    private void cancelar(Pedido pedido, String motivo) {
        pedido.cancelar(motivo);
        inventario.liberar(pedido.getId());
        try {
            avisarParticipantes(pedido, TipoMensaje.LIBERAR);
        } catch (RuntimeException e) {
            System.err.println("Aviso: no se pudo enviar LIBERAR del pedido " + pedido.getId() + ": " + e.getMessage());
        }
    }

    /** Envía CONFIRMAR o LIBERAR a cada sede (distinta de esta) que aporta productos. */
    private void avisarParticipantes(Pedido pedido, TipoMensaje tipo) {
        for (String sede : pedido.getSedesParticipantes()) {
            if (!sede.equals(codigoLocal)) {
                Mensaje m = new Mensaje(tipo, codigoLocal, sede);
                m.setPedidoId(pedido.getId());
                canal.enviar(m);
            }
        }
    }

    /** Las RESPUESTA a nuestros RESERVAR despiertan al pedido que las espera. */
    public void procesar(Mensaje m) {
        if (m == null || m.getTipo() != TipoMensaje.RESPUESTA || !codigoLocal.equals(m.getPara())
                || m.getPedidoId() == null) {
            return;
        }
        EsperaReservas destino = null;
        synchronized (esperas) {
            for (EsperaReservas espera : esperas.toArray(new EsperaReservas[0])) {
                if (espera.getPedidoId().equals(m.getPedidoId())) {
                    destino = espera;
                    break;
                }
            }
        }
        if (destino != null) {
            destino.recibir(m);
        }
        // Si nadie la espera, llegó tarde: el pedido ya se canceló y se envió LIBERAR.
    }

    // ---------- Consultas y entregas ----------

    /** Historial: todos los pedidos de la sede, en cualquier estado. */
    public synchronized Pedido[] pedidos() {
        return pedidos.toArray(new Pedido[0]);
    }

    /**
     * La cola real de pedidos que esperan entrega (la usa DespachoService de P2).
     * Quien la recorra o la modifique debe hacerlo dentro de {@code synchronized (cola) { ... }}.
     */
    public Cola<Pedido> pendientes() {
        return pendientes;
    }

    /** Copia de los pendientes en orden de llegada, para la API. */
    public Pedido[] pendientesArreglo() {
        synchronized (pendientes) {
            return pendientes.toArray(new Pedido[0]);
        }
    }

    /** Historial de ventas de la sede. */
    public synchronized Venta[] ventas() {
        return ventas.toArray(new Venta[0]);
    }

    /**
     * Para DespachoService (P2): el pedido llegó a su destino. Lo marca ENTREGADO, guarda el
     * tiempo en el pedido y en su venta y guarda los archivos. Devuelve false si no existe.
     */
    public boolean marcarEntregado(String pedidoId, int tiempoEntrega) {
        boolean encontrado = false;
        synchronized (this) {
            for (Pedido pedido : pedidos.toArray(new Pedido[0])) {
                if (pedido.getId().equals(pedidoId)) {
                    pedido.setEstado(EstadoPedido.ENTREGADO);
                    pedido.setTiempoTraslado(tiempoEntrega);
                    encontrado = true;
                }
            }
            for (Venta venta : ventas.toArray(new Venta[0])) {
                if (venta.getPedidoId().equals(pedidoId)) {
                    venta.setTiempoEntrega(tiempoEntrega);
                }
            }
        }
        if (encontrado) {
            guardar();
        }
        return encontrado;
    }

    // ---------- Persistencia ----------

    /** Guarda pedidos y ventas. Lo puede llamar DespachoService después de cambiar un estado. */
    public synchronized void guardar() {
        if (almacen == null) {
            return;
        }
        almacen.guardar(ARCHIVO_PEDIDOS, pedidos.toArray(new Pedido[0]));
        almacen.guardar(ARCHIVO_VENTAS, ventas.toArray(new Venta[0]));
    }

    /** Reconstruye historial, ventas y cola de pendientes desde los archivos de la sede. */
    private void cargar() {
        if (almacen == null) {
            return;
        }
        for (Pedido pedido : almacen.cargar(ARCHIVO_PEDIDOS, Pedido[].class)) {
            if (pedido == null || pedido.getId() == null) {
                continue;
            }
            if (pedido.getLineas() == null) {
                pedido.setLineas(new LineaPedido[0]);
            }
            pedidos.agregar(pedido);
            consecutivo = Math.max(consecutivo, numeroDe(pedido.getId()));
            if (pedido.getEstado() == EstadoPedido.PENDIENTE || pedido.getEstado() == EstadoPedido.EN_RUTA) {
                pendientes.encolar(pedido);
            }
        }
        for (Venta venta : almacen.cargar(ARCHIVO_VENTAS, Venta[].class)) {
            if (venta != null && venta.getPedidoId() != null) {
                ventas.agregar(venta);
            }
        }
    }

    private String nuevoId() {
        consecutivo++;
        return codigoLocal + "-" + (1000 + consecutivo);
    }

    /** "ALA-1007" → 7 (el consecutivo); 0 si el id no tiene ese formato. */
    private static int numeroDe(String id) {
        int guion = id.lastIndexOf('-');
        try {
            return Integer.parseInt(id.substring(guion + 1)) - 1000;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String ahora() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
