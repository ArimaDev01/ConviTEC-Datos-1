package convitec.modelo;

/**
 * Venta registrada al confirmar un pedido (respuesta del profe). Pertenece a la sede
 * que registró el pedido y guarda qué sedes aportaron productos.
 * tiempoEntrega se llena al entregar: es la suma de los tramos del recorrido hasta
 * ese destino. En una venta de mostrador es 0.
 */
public class Venta {
    private String pedidoId;
    private String fecha;
    private String sede;
    private String[] sedesParticipantes;
    private boolean mostrador;
    private int tiempoEntrega;
    private LineaPedido[] lineas;
    private double montoTotal;

    public Venta() {
    }

    /** Crea la venta a partir de un pedido confirmado. */
    public Venta(Pedido pedido, String fecha) {
        this.pedidoId = pedido.getId();
        this.fecha = fecha;
        this.sede = pedido.getSede();
        this.sedesParticipantes = pedido.getSedesParticipantes();
        this.mostrador = pedido.isMostrador();
        this.tiempoEntrega = 0;
        this.lineas = pedido.getLineas();
        this.montoTotal = pedido.getTotal();
    }

    public String getPedidoId() { return pedidoId; }
    public String getFecha() { return fecha; }
    public String getSede() { return sede; }
    public String[] getSedesParticipantes() { return sedesParticipantes; }
    public boolean isMostrador() { return mostrador; }
    public int getTiempoEntrega() { return tiempoEntrega; }
    public LineaPedido[] getLineas() { return lineas; }
    public double getMontoTotal() { return montoTotal; }

    public void setTiempoEntrega(int tiempoEntrega) { this.tiempoEntrega = tiempoEntrega; }

    /** Si cuenta para "peores pedidos": las de mostrador o con tiempo 0 no (evita dividir entre 0). */
    public boolean tieneRendimiento() {
        return !mostrador && tiempoEntrega > 0;
    }

    /** Rendimiento = monto de venta (₡) / tiempo de entrega (min). Usar solo si tieneRendimiento(). */
    public double getRendimiento() {
        if (!tieneRendimiento()) {
            throw new IllegalStateException("La venta " + pedidoId + " no tiene tiempo de entrega");
        }
        return montoTotal / tiempoEntrega;
    }
}
