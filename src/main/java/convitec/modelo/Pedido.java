package convitec.modelo;

/**
 * Pedido registrado en una sede. Las fechas se guardan como texto ISO
 * (por ejemplo "2026-11-25T14:30:00") para que Gson las guarde sin problemas.
 */
public class Pedido {
    private String id;
    private String sede;
    private String fechaCreacion;
    private EstadoPedido estado;
    private String destino;
    private boolean mostrador;
    private LineaPedido[] lineas;
    private int tiempoTraslado;
    private String motivoCancelacion;

    public Pedido() {
    }

    public Pedido(String id, String sede, String fechaCreacion, String destino, boolean mostrador, LineaPedido[] lineas) {
        this.id = id;
        this.sede = sede;
        this.fechaCreacion = fechaCreacion;
        this.destino = destino;
        this.mostrador = mostrador;
        this.lineas = lineas == null ? new LineaPedido[0] : lineas;
        this.estado = EstadoPedido.RESERVANDO;
    }

    public String getId() { return id; }
    public String getSede() { return sede; }
    public String getFechaCreacion() { return fechaCreacion; }
    public EstadoPedido getEstado() { return estado; }
    public String getDestino() { return destino; }
    public boolean isMostrador() { return mostrador; }
    public LineaPedido[] getLineas() { return lineas; }
    public int getTiempoTraslado() { return tiempoTraslado; }
    public String getMotivoCancelacion() { return motivoCancelacion; }

    public void setEstado(EstadoPedido estado) { this.estado = estado; }
    public void setLineas(LineaPedido[] lineas) { this.lineas = lineas; }
    public void setTiempoTraslado(int tiempoTraslado) { this.tiempoTraslado = tiempoTraslado; }

    /** Marca el pedido como cancelado y guarda el motivo que se le muestra al dependiente. */
    public void cancelar(String motivo) {
        this.estado = EstadoPedido.CANCELADO;
        this.motivoCancelacion = motivo;
    }

    /** Suma de los subtotales de todas las líneas. */
    public double getTotal() {
        double total = 0;
        for (LineaPedido linea : lineas) {
            total += linea.getSubtotal();
        }
        return total;
    }

    /** Códigos de las sedes que aportan productos, sin repetir y en el orden en que aparecen. */
    public String[] getSedesParticipantes() {
        String[] temporal = new String[lineas.length];
        int cantidad = 0;
        for (LineaPedido linea : lineas) {
            String sedeLinea = linea.getSedeQueSuministra();
            if (sedeLinea != null && !contiene(temporal, cantidad, sedeLinea)) {
                temporal[cantidad] = sedeLinea;
                cantidad++;
            }
        }
        String[] resultado = new String[cantidad];
        for (int i = 0; i < cantidad; i++) {
            resultado[i] = temporal[i];
        }
        return resultado;
    }

    private static boolean contiene(String[] arreglo, int usados, String valor) {
        for (int i = 0; i < usados; i++) {
            if (arreglo[i].equals(valor)) {
                return true;
            }
        }
        return false;
    }
}
