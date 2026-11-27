package convitec.api;

import convitec.estructuras.ArbolAVL;
import convitec.estructuras.Cola;
import convitec.estructuras.ListaEnlazada;
import convitec.modelo.EstadoPedido;
import convitec.modelo.LineaPedido;
import convitec.modelo.NodoCatalogo;
import convitec.modelo.PasoRecorrido;
import convitec.modelo.Pedido;
import convitec.modelo.Producto;
import convitec.modelo.Recorrido;
import convitec.modelo.ResultadoPedido;
import convitec.modelo.Sede;
import convitec.modelo.Venta;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Versión falsa de la sede para probar la API y la página antes de que existan
 * InventarioService, PedidoService y DespachoService. Todo vive en memoria y se pierde al cerrar.
 *
 * Imita las reglas del enunciado de forma simple:
 * - Guanacaste (Liberia y Cañas) es un componente aislado: solo se llega desde Liberia y viceversa.
 * - Un pedido toma primero de la sede local y lo que falte de las demás sedes.
 * - Las entregas usan minutos fijos por destino en vez de Dijkstra.
 * Cuando lleguen los servicios reales, App usa otra implementación de DatosSede y esta clase queda solo para pruebas.
 */
public class DatosFalsos implements DatosSede {

    private static final String[] GUANACASTE = {"Liberia", "Cañas"};

    private final Sede[] sedes;
    private final int indiceSede;
    private final String[] destinos;
    private final ArbolAVL<NodoCatalogo> catalogo = new ArbolAVL<>();
    private final ListaEnlazada<Pedido> pedidos = new ListaEnlazada<>();
    private final Cola<Pedido> pendientes = new Cola<>();
    private final ListaEnlazada<Venta> ventas = new ListaEnlazada<>();
    private final ListaEnlazada<Recorrido> recorridos = new ListaEnlazada<>();
    private Recorrido recorridoActual;
    private String ubicacionActual;
    private int consecutivo;

    /** Sedes y destinos de ejemplo del enunciado, con la sede indicada como actual. */
    public DatosFalsos(String codigoSede) {
        this(sedesDeEjemplo(), codigoSede, destinosDeEjemplo(), productosDeEjemplo(4));
    }

    /**
     * @param sedes     las sedes en el orden de sedes.json
     * @param codigo    la sede que se está arrancando
     * @param destinos  los lugares del grafo (puntos de entrega posibles)
     * @param productos el catálogo inicial; cada nodo con una existencia por sede
     */
    public DatosFalsos(Sede[] sedes, String codigo, String[] destinos, NodoCatalogo[] productos) {
        this.sedes = sedes;
        this.indiceSede = buscarSede(sedes, codigo);
        this.destinos = destinos;
        for (NodoCatalogo nodo : productos) {
            catalogo.insertar(nodo.getCodigo(), nodo);
        }
        this.ubicacionActual = sedes[indiceSede].getUbicacion();
    }

    @Override
    public synchronized InfoSede info() {
        return new InfoSede(sedes[indiceSede], indiceSede, sedes, destinos, horaVirtual(), true);
    }

    @Override
    public synchronized NodoCatalogo[] catalogo() {
        return catalogo.enOrden(new NodoCatalogo[0]);
    }

    @Override
    public synchronized void agregarUnidades(String codigo, int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que 0");
        }
        NodoCatalogo nodo = catalogo.buscar(codigo);
        if (nodo == null) {
            throw new IllegalArgumentException("No existe el producto " + codigo);
        }
        nodo.setExistencia(indiceSede, nodo.getExistencia(indiceSede) + cantidad);
    }

    @Override
    public synchronized ResultadoPedido registrarPedido(String destino, boolean mostrador, LineaPedido[] items) {
        consecutivo++;
        String id = sedes[indiceSede].getCodigo() + "-" + (1000 + consecutivo);
        String lugar = mostrador ? sedes[indiceSede].getUbicacion() : destino;
        LineaPedido[] sumadas = sumarRepetidas(items);
        Pedido pedido = new Pedido(id, sedes[indiceSede].getCodigo(), ahora(), lugar, mostrador, sumadas);
        pedidos.agregar(pedido);

        if (!mostrador && !alcanzable(destino)) {
            pedido.cancelar("No hay ruta desde " + sedes[indiceSede].getUbicacion() + " hasta " + destino);
            return ResultadoPedido.rechazado(pedido.getMotivoCancelacion(), pedido);
        }
        for (LineaPedido linea : sumadas) {
            NodoCatalogo nodo = catalogo.buscar(linea.getCodigo());
            if (nodo == null) {
                pedido.cancelar("No existe el producto " + linea.getCodigo());
                return ResultadoPedido.rechazado(pedido.getMotivoCancelacion(), pedido);
            }
            if (nodo.getExistenciaTotal() < linea.getCantidad()) {
                pedido.cancelar("No hay suficiente " + nodo.getProducto().getNombre() + ": se piden "
                        + linea.getCantidad() + " y hay " + nodo.getExistenciaTotal() + " en todas las sedes");
                return ResultadoPedido.rechazado(pedido.getMotivoCancelacion(), pedido);
            }
        }

        pedido.setLineas(asignarSedes(sumadas));
        Venta venta = new Venta(pedido, pedido.getFechaCreacion());
        ventas.agregar(venta);
        if (mostrador) {
            pedido.setEstado(EstadoPedido.ENTREGADO);
        } else {
            pedido.setEstado(EstadoPedido.PENDIENTE);
            pendientes.encolar(pedido);
        }
        return ResultadoPedido.aceptado(pedido);
    }

    @Override
    public synchronized Pedido[] pedidos() {
        return pedidos.toArray(new Pedido[0]);
    }

    @Override
    public synchronized Pedido[] pendientes() {
        return pendientes.toArray(new Pedido[0]);
    }

    @Override
    public synchronized PasoRecorrido siguientePaso() {
        if (pendientes.estaVacia()) {
            terminarRecorrido();
            return null;
        }
        if (recorridoActual == null) {
            recorridoActual = new Recorrido(sedes[indiceSede].getCodigo(), horaVirtual());
            recorridos.agregar(recorridoActual);
            ubicacionActual = sedes[indiceSede].getUbicacion();
        }
        // Falso: el "más cercano" es el primero de la cola; se entregan todos los del mismo destino.
        String destino = pendientes.verFrente().getDestino();
        int minutos = minutosFalsos(ubicacionActual, destino);
        int acumulado = recorridoActual.getTiempoTotal() + minutos;

        Cola<Pedido> quedan = new Cola<>();
        ListaEnlazada<String> entregados = new ListaEnlazada<>();
        while (!pendientes.estaVacia()) {
            Pedido p = pendientes.desencolar();
            if (p.getDestino().equals(destino)) {
                p.setEstado(EstadoPedido.ENTREGADO);
                p.setTiempoTraslado(acumulado);
                Venta venta = ventaDe(p.getId());
                if (venta != null) {
                    venta.setTiempoEntrega(acumulado);
                }
                entregados.agregar(p.getId());
            } else {
                quedan.encolar(p);
            }
        }
        while (!quedan.estaVacia()) {
            pendientes.encolar(quedan.desencolar());
        }

        PasoRecorrido paso = new PasoRecorrido(ubicacionActual, destino, new String[]{ubicacionActual, destino},
                minutos, acumulado, entregados.toArray(new String[0]));
        agregarPaso(paso);
        recorridoActual.setTiempoTotal(acumulado);
        ubicacionActual = destino;
        if (pendientes.estaVacia()) {
            terminarRecorrido();
        }
        return paso;
    }

    @Override
    public synchronized Recorrido[] recorridos() {
        return recorridos.toArray(new Recorrido[0]);
    }

    @Override
    public synchronized Venta[] ventas() {
        return ventas.toArray(new Venta[0]);
    }

    // ---------- Ayudantes ----------

    private boolean alcanzable(String destino) {
        if (destino == null || !existeDestino(destino)) {
            return false;
        }
        return esDeGuanacaste(sedes[indiceSede].getUbicacion()) == esDeGuanacaste(destino);
    }

    private boolean existeDestino(String destino) {
        for (String d : destinos) {
            if (d.equals(destino)) {
                return true;
            }
        }
        return false;
    }

    private static boolean esDeGuanacaste(String lugar) {
        for (String g : GUANACASTE) {
            if (g.equals(lugar)) {
                return true;
            }
        }
        return false;
    }

    /** Une las líneas del mismo producto y completa nombre y precio desde el catálogo. */
    private LineaPedido[] sumarRepetidas(LineaPedido[] items) {
        ArbolAVL<LineaPedido> porCodigo = new ArbolAVL<>();
        ListaEnlazada<LineaPedido> orden = new ListaEnlazada<>();
        for (LineaPedido item : items) {
            LineaPedido existente = porCodigo.buscar(item.getCodigo());
            if (existente != null) {
                existente.setCantidad(existente.getCantidad() + item.getCantidad());
                continue;
            }
            LineaPedido nueva = new LineaPedido(item.getCodigo(), item.getCantidad());
            NodoCatalogo nodo = catalogo.buscar(item.getCodigo());
            if (nodo != null) {
                nueva.setNombre(nodo.getProducto().getNombre());
                nueva.setPrecioUnitario(nodo.getProducto().getPrecio());
            }
            porCodigo.insertar(item.getCodigo(), nueva);
            orden.agregar(nueva);
        }
        return orden.toArray(new LineaPedido[0]);
    }

    /** Rebaja existencias tomando primero de la sede local; una línea por cada sede que aporta. */
    private LineaPedido[] asignarSedes(LineaPedido[] lineas) {
        ListaEnlazada<LineaPedido> resultado = new ListaEnlazada<>();
        for (LineaPedido linea : lineas) {
            NodoCatalogo nodo = catalogo.buscar(linea.getCodigo());
            int falta = linea.getCantidad();
            for (int paso = 0; paso < sedes.length && falta > 0; paso++) {
                int s = (indiceSede + paso) % sedes.length;
                int tomar = Math.min(falta, nodo.getExistencia(s));
                if (tomar > 0) {
                    nodo.setExistencia(s, nodo.getExistencia(s) - tomar);
                    resultado.agregar(new LineaPedido(linea.getCodigo(), linea.getNombre(), tomar,
                            linea.getPrecioUnitario(), sedes[s].getCodigo()));
                    falta -= tomar;
                }
            }
        }
        return resultado.toArray(new LineaPedido[0]);
    }

    private Venta ventaDe(String pedidoId) {
        for (int i = 0; i < ventas.tamano(); i++) {
            if (ventas.obtener(i).getPedidoId().equals(pedidoId)) {
                return ventas.obtener(i);
            }
        }
        return null;
    }

    private void agregarPaso(PasoRecorrido paso) {
        PasoRecorrido[] anteriores = recorridoActual.getPasos();
        PasoRecorrido[] nuevos = new PasoRecorrido[anteriores.length + 1];
        for (int i = 0; i < anteriores.length; i++) {
            nuevos[i] = anteriores[i];
        }
        nuevos[anteriores.length] = paso;
        recorridoActual.setPasos(nuevos);
    }

    private void terminarRecorrido() {
        if (recorridoActual != null) {
            recorridoActual.setTerminado(true);
            recorridoActual = null;
        }
        ubicacionActual = sedes[indiceSede].getUbicacion();
    }

    /** Minutos inventados pero estables: dependen solo de los nombres de los lugares. */
    private static int minutosFalsos(String desde, String hasta) {
        if (desde.equals(hasta)) {
            return 0;
        }
        int suma = 0;
        String texto = desde + hasta;
        for (int i = 0; i < texto.length(); i++) {
            suma += texto.charAt(i);
        }
        return 15 + suma % 60;
    }

    /** Hora virtual igual a la que usará el RelojVirtual de P2: minutos del día = segundos epoch % 1440. */
    static String horaVirtual() {
        long minutos = (System.currentTimeMillis() / 1000) % 1440;
        return String.format("%02d:%02d", minutos / 60, minutos % 60);
    }

    private static String ahora() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private static int buscarSede(Sede[] sedes, String codigo) {
        for (int i = 0; i < sedes.length; i++) {
            if (sedes[i].getCodigo().equals(codigo)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Sede desconocida: " + codigo);
    }

    // ---------- Datos de ejemplo ----------

    static Sede[] sedesDeEjemplo() {
        return new Sede[]{
                new Sede("LIB", "Liberia", "Liberia", 7001),
                new Sede("ALA", "Alajuela", "Alajuela", 7002),
                new Sede("SJO", "San José", "San José", 7003),
                new Sede("LIM", "Limón", "Limón", 7004)
        };
    }

    static String[] destinosDeEjemplo() {
        return new String[]{"Liberia", "Cañas", "Puntarenas", "Orotina", "Alajuela", "San José", "Heredia",
                "Cartago", "Paraíso", "Turrialba", "Guápiles", "Siquirres", "Limón"};
    }

    static NodoCatalogo[] productosDeEjemplo(int cantidadSedes) {
        Object[][] filas = {
                {"P01", "Agua 600 ml", "Agua purificada", "Bebidas", 650.0, new int[]{20, 15, 30, 10}},
                {"P02", "Café molido 250 g", "Café tostado de altura", "Abarrotes", 2450.0, new int[]{5, 12, 0, 8}},
                {"P03", "Galletas de soda", "Paquete de 6 unidades", "Snacks", 900.0, new int[]{10, 0, 25, 4}},
                {"P04", "Leche 1 L", "Leche semidescremada", "Lácteos", 1100.0, new int[]{0, 18, 14, 6}},
                {"P05", "Arroz 1 kg", "Arroz 99 % grano entero", "Abarrotes", 1350.0, new int[]{8, 9, 20, 0}},
                {"P06", "Refresco 2 L", "Refresco de cola", "Bebidas", 1600.0, new int[]{12, 6, 0, 9}},
                {"P07", "Papas tostadas", "Bolsa de 150 g", "Snacks", 1200.0, new int[]{0, 7, 11, 3}},
                {"P08", "Queso fresco 500 g", "Queso tierno", "Lácteos", 2800.0, new int[]{4, 0, 5, 2}},
        };
        NodoCatalogo[] nodos = new NodoCatalogo[filas.length];
        for (int i = 0; i < filas.length; i++) {
            Object[] f = filas[i];
            nodos[i] = new NodoCatalogo(new Producto((String) f[0], (String) f[1], (String) f[2], (String) f[3],
                    (double) f[4]), cantidadSedes);
            int[] existencias = (int[]) f[5];
            for (int s = 0; s < cantidadSedes && s < existencias.length; s++) {
                nodos[i].setExistencia(s, existencias[s]);
            }
        }
        return nodos;
    }
}
