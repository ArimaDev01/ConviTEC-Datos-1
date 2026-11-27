package convitec.servicios;

import convitec.estructuras.ArbolAVL;
import convitec.estructuras.ListaEnlazada;
import convitec.mensajeria.CanalMensajes;
import convitec.modelo.Mensaje;
import convitec.modelo.NodoCatalogo;
import convitec.modelo.Reserva;
import convitec.modelo.Sede;
import convitec.modelo.TipoMensaje;
import convitec.persistencia.AlmacenJson;
import convitec.persistencia.Config;

/**
 * Inventario de una sede: un árbol AVL de NodoCatalogo con el código de producto como clave.
 *
 * Regla principal: <b>solo la sede dueña toca su existencia</b>. La casilla de esta sede en
 * cada NodoCatalogo es el inventario local; las casillas de las otras sedes son una copia que
 * solo cambia cuando llega un STOCK de la sede dueña. Así nunca hay dos sedes peleando por
 * el mismo número: si otra sede quiere unidades, las pide con RESERVAR y esta sede decide.
 *
 * Mensajes que procesa (los demás los ignora):
 * <pre>
 *   STOCK      otra sede avisa su nueva existencia de un producto → se actualiza la copia
 *   HOLA       otra sede acaba de arrancar → se le responde un STOCK por producto
 *   RESERVAR   otra sede pide apartar unidades → se rebajan y se responde RESPUESTA ok,
 *              o se responde RESPUESTA rechazada con el motivo
 *   CONFIRMAR  el pedido se confirmó → la reserva queda rebajada para siempre
 *   LIBERAR    el pedido se canceló → se devuelven las unidades reservadas
 * </pre>
 * CONFIRMAR y LIBERAR repetidos no hacen nada la segunda vez, y un RESERVAR que llega después
 * de que el pedido se cerró (por ejemplo, la sede estaba apagada y lo procesa al arrancar)
 * se rechaza sin rebajar nada.
 *
 * Todos los métodos públicos son synchronized: los llaman a la vez el hilo de RabbitMQ y los
 * hilos del servidor web.
 *
 * Archivos en data/SEDE/: inventario.json, reservas.json (reservas abiertas) y
 * cerrados.json (pedidos ya confirmados o liberados).
 */
public class InventarioService {
    static final String ARCHIVO_INVENTARIO = "inventario.json";
    static final String ARCHIVO_RESERVAS = "reservas.json";
    static final String ARCHIVO_CERRADOS = "cerrados.json";

    private final Sede[] sedes;
    private final int indiceLocal;
    private final String codigoLocal;
    private final CanalMensajes canal;
    private final AlmacenJson almacen;

    private final ArbolAVL<NodoCatalogo> catalogo = new ArbolAVL<>();
    private final ListaEnlazada<Reserva> reservas = new ListaEnlazada<>();
    /** Pedidos ya confirmados o liberados en esta sede. El AVL sirve de conjunto: clave y valor = pedidoId. */
    private final ArbolAVL<String> cerrados = new ArbolAVL<>();

    /**
     * @param sedes       las sedes en el orden de sedes.json (define el índice de cada una)
     * @param indiceLocal índice de esta sede
     * @param iniciales   catálogo de productos.json, se usa si todavía no hay inventario.json
     * @param canal       por donde se envían y reciben mensajes
     * @param almacen     donde se guardan los archivos; null para no guardar nada (pruebas)
     */
    public InventarioService(Sede[] sedes, int indiceLocal, NodoCatalogo[] iniciales,
                             CanalMensajes canal, AlmacenJson almacen) {
        if (sedes == null || indiceLocal < 0 || indiceLocal >= sedes.length) {
            throw new IllegalArgumentException("Sede local inválida");
        }
        if (canal == null) {
            throw new IllegalArgumentException("El inventario necesita un canal de mensajes");
        }
        this.sedes = sedes;
        this.indiceLocal = indiceLocal;
        this.codigoLocal = sedes[indiceLocal].getCodigo();
        this.canal = canal;
        this.almacen = almacen;
        cargar(iniciales == null ? new NodoCatalogo[0] : iniciales);
    }

    /** Crea el inventario con la configuración de la sede y su carpeta de datos. */
    public static InventarioService crear(Config config, CanalMensajes canal) {
        return new InventarioService(config.getSedes(), config.getIndiceSedeActual(),
                config.getProductosIniciales(), canal, new AlmacenJson(config.getCarpetaDatosSede()));
    }

    /**
     * Empieza a escuchar mensajes, saluda con HOLA (las demás responden con su existencia)
     * y avisa la existencia propia de cada producto, por si las demás tenían un dato viejo.
     */
    public void iniciar() {
        canal.alRecibir(this::procesar);
        canal.enviar(new Mensaje(TipoMensaje.HOLA, codigoLocal, Mensaje.TODOS));
        synchronized (this) {
            for (NodoCatalogo nodo : catalogo.enOrden(new NodoCatalogo[0])) {
                enviarStock(nodo, Mensaje.TODOS);
            }
        }
    }

    // ---------- Consultas ----------

    /** Catálogo global ordenado por código. */
    public synchronized NodoCatalogo[] productos() {
        return catalogo.enOrden(new NodoCatalogo[0]);
    }

    /** El nodo de un producto, o null si no existe. */
    public synchronized NodoCatalogo buscar(String codigo) {
        return catalogo.buscar(codigo);
    }

    /** Existencia de un producto en una sede (índice de sedes.json). */
    public synchronized int existencia(String codigo, int sede) {
        NodoCatalogo nodo = catalogo.buscar(codigo);
        if (nodo == null) {
            throw new IllegalArgumentException("No existe el producto " + codigo);
        }
        return nodo.getExistencia(sede);
    }

    public Sede[] getSedes() {
        Sede[] copia = new Sede[sedes.length];
        for (int i = 0; i < sedes.length; i++) {
            copia[i] = sedes[i];
        }
        return copia;
    }

    public int getIndiceLocal() {
        return indiceLocal;
    }

    public Sede getSedeLocal() {
        return sedes[indiceLocal];
    }

    /** Índice de una sede por su código, o -1. */
    public int indiceDe(String codigoSede) {
        for (int i = 0; i < sedes.length; i++) {
            if (sedes[i].getCodigo().equals(codigoSede)) {
                return i;
            }
        }
        return -1;
    }

    /** Reservas abiertas (rebajadas, esperando CONFIRMAR o LIBERAR). */
    public synchronized Reserva[] reservas() {
        return reservas.toArray(new Reserva[0]);
    }

    // ---------- Cambios en el inventario local ----------

    /** Suma unidades al inventario local y avisa a las demás sedes con STOCK. */
    public synchronized void agregarUnidades(String codigo, int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que 0");
        }
        NodoCatalogo nodo = catalogo.buscar(codigo);
        if (nodo == null) {
            throw new IllegalArgumentException("No existe el producto " + codigo);
        }
        nodo.setExistencia(indiceLocal, nodo.getExistencia(indiceLocal) + cantidad);
        guardar();
        enviarStock(nodo, Mensaje.TODOS);
    }

    /**
     * Aparta unidades del inventario local para un pedido. Lo usa esta sede para sus
     * propios pedidos y también para atender un RESERVAR de otra sede.
     *
     * @return null si se reservó, o el motivo del rechazo
     */
    public synchronized String reservar(String pedidoId, String sedeSolicitante, String codigo, int cantidad) {
        if (pedidoId == null || codigo == null) {
            return "Solicitud de reserva incompleta";
        }
        if (cerrados.contiene(pedidoId)) {
            return "El pedido " + pedidoId + " ya fue cerrado";
        }
        if (buscarReserva(pedidoId, codigo) != null) {
            return null; // RESERVAR repetido: ya estaba apartado, no se rebaja dos veces
        }
        NodoCatalogo nodo = catalogo.buscar(codigo);
        if (nodo == null) {
            return "La sede " + codigoLocal + " no tiene el producto " + codigo;
        }
        if (cantidad <= 0) {
            return "Cantidad inválida para " + codigo;
        }
        int disponible = nodo.getExistencia(indiceLocal);
        if (disponible < cantidad) {
            return "La sede " + codigoLocal + " solo tiene " + disponible + " de " + nodo.getProducto().getNombre()
                    + " (se pidieron " + cantidad + ")";
        }
        nodo.setExistencia(indiceLocal, disponible - cantidad);
        reservas.agregar(new Reserva(pedidoId, sedeSolicitante, codigo, cantidad));
        guardar();
        enviarStock(nodo, Mensaje.TODOS);
        return null;
    }

    /** El pedido se confirmó: sus reservas quedan rebajadas. Repetido no hace nada. */
    public synchronized void confirmar(String pedidoId) {
        if (pedidoId == null || cerrados.contiene(pedidoId)) {
            return;
        }
        Reserva[] delPedido = reservasDe(pedidoId);
        for (Reserva reserva : delPedido) {
            reservas.eliminar(reserva);
        }
        cerrados.insertar(pedidoId, pedidoId);
        guardar();
    }

    /** El pedido se canceló: se devuelven sus unidades. Repetido no hace nada. */
    public synchronized void liberar(String pedidoId) {
        if (pedidoId == null || cerrados.contiene(pedidoId)) {
            return;
        }
        Reserva[] delPedido = reservasDe(pedidoId);
        for (Reserva reserva : delPedido) {
            reservas.eliminar(reserva);
            NodoCatalogo nodo = catalogo.buscar(reserva.getCodigo());
            if (nodo != null) {
                nodo.setExistencia(indiceLocal, nodo.getExistencia(indiceLocal) + reserva.getCantidad());
            }
        }
        cerrados.insertar(pedidoId, pedidoId);
        guardar();
        for (Reserva reserva : delPedido) {
            NodoCatalogo nodo = catalogo.buscar(reserva.getCodigo());
            if (nodo != null) {
                enviarStock(nodo, Mensaje.TODOS);
            }
        }
    }

    // ---------- Mensajes ----------

    /** Procesa un mensaje que llegó por el canal. Los tipos que no le tocan al inventario se ignoran. */
    public synchronized void procesar(Mensaje m) {
        if (m == null || m.getTipo() == null || m.getDe() == null || m.getDe().equals(codigoLocal)
                || !m.esPara(codigoLocal)) {
            return;
        }
        switch (m.getTipo()) {
            case STOCK -> recibirStock(m);
            case HOLA -> responderHola(m);
            case RESERVAR -> atenderReserva(m);
            case CONFIRMAR -> confirmar(m.getPedidoId());
            case LIBERAR -> liberar(m.getPedidoId());
            default -> { } // RESPUESTA la procesa PedidoService
        }
    }

    private void recibirStock(Mensaje m) {
        int indice = indiceDe(m.getDe());
        NodoCatalogo nodo = catalogo.buscar(m.getCodigo());
        if (indice < 0 || indice == indiceLocal || nodo == null || m.getCantidad() == null || m.getCantidad() < 0) {
            return;
        }
        if (nodo.getExistencia(indice) != m.getCantidad()) {
            nodo.setExistencia(indice, m.getCantidad());
            guardar();
        }
    }

    private void responderHola(Mensaje m) {
        for (NodoCatalogo nodo : catalogo.enOrden(new NodoCatalogo[0])) {
            enviarStock(nodo, m.getDe());
        }
    }

    private void atenderReserva(Mensaje m) {
        int cantidad = m.getCantidad() == null ? 0 : m.getCantidad();
        String motivo = reservar(m.getPedidoId(), m.getDe(), m.getCodigo(), cantidad);
        Mensaje respuesta = new Mensaje(TipoMensaje.RESPUESTA, codigoLocal, m.getDe());
        respuesta.setPedidoId(m.getPedidoId());
        respuesta.setCodigo(m.getCodigo());
        respuesta.setCantidad(cantidad);
        respuesta.setOk(motivo == null);
        respuesta.setMotivo(motivo);
        canal.enviar(respuesta);
        NodoCatalogo nodo = catalogo.buscar(m.getCodigo());
        if (motivo != null && nodo != null) {
            // Si la otra sede pidió más de lo que hay, su copia estaba vieja: se le corrige.
            enviarStock(nodo, m.getDe());
        }
    }

    private void enviarStock(NodoCatalogo nodo, String para) {
        Mensaje stock = new Mensaje(TipoMensaje.STOCK, codigoLocal, para);
        stock.setCodigo(nodo.getCodigo());
        stock.setCantidad(nodo.getExistencia(indiceLocal));
        canal.enviar(stock);
    }

    // ---------- Apoyo ----------

    private Reserva buscarReserva(String pedidoId, String codigo) {
        for (Reserva reserva : reservas.toArray(new Reserva[0])) {
            if (reserva.getPedidoId().equals(pedidoId) && reserva.getCodigo().equals(codigo)) {
                return reserva;
            }
        }
        return null;
    }

    private Reserva[] reservasDe(String pedidoId) {
        ListaEnlazada<Reserva> encontradas = new ListaEnlazada<>();
        for (Reserva reserva : reservas.toArray(new Reserva[0])) {
            if (reserva.getPedidoId().equals(pedidoId)) {
                encontradas.agregar(reserva);
            }
        }
        return encontradas.toArray(new Reserva[0]);
    }

    // ---------- Persistencia ----------

    /**
     * Reconstruye las estructuras al arrancar. Si ya hay inventario.json se usa ese (con la
     * existencia que quedó la última vez); si no, el catálogo de productos.json. Un producto
     * nuevo en productos.json que no esté en el archivo guardado se agrega.
     */
    private void cargar(NodoCatalogo[] iniciales) {
        NodoCatalogo[] guardados = almacen == null ? new NodoCatalogo[0]
                : almacen.cargar(ARCHIVO_INVENTARIO, NodoCatalogo[].class);
        for (NodoCatalogo nodo : guardados) {
            if (nodo != null && nodo.getProducto() != null && nodo.getCantidadSedes() == sedes.length) {
                catalogo.insertar(nodo.getCodigo(), nodo);
            }
        }
        for (NodoCatalogo nodo : iniciales) {
            if (!catalogo.contiene(nodo.getCodigo())) {
                catalogo.insertar(nodo.getCodigo(), nodo);
            }
        }
        if (almacen == null) {
            return;
        }
        for (Reserva reserva : almacen.cargar(ARCHIVO_RESERVAS, Reserva[].class)) {
            if (reserva != null && reserva.getPedidoId() != null && reserva.getCodigo() != null) {
                reservas.agregar(reserva);
            }
        }
        for (String pedidoId : almacen.cargar(ARCHIVO_CERRADOS, String[].class)) {
            if (pedidoId != null) {
                cerrados.insertar(pedidoId, pedidoId);
            }
        }
        guardar();
    }

    private void guardar() {
        if (almacen == null) {
            return;
        }
        almacen.guardar(ARCHIVO_INVENTARIO, catalogo.enOrden(new NodoCatalogo[0]));
        almacen.guardar(ARCHIVO_RESERVAS, reservas.toArray(new Reserva[0]));
        almacen.guardar(ARCHIVO_CERRADOS, clavesCerrados());
    }

    /** En el AVL de cerrados el valor es el mismo pedidoId, así enOrden da la lista de ids. */
    private String[] clavesCerrados() {
        return cerrados.enOrden(new String[0]);
    }
}
