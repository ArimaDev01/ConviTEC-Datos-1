package convitec.modelo;

/** Unidades que una sede apartó para un pedido de otra sede, hasta que llegue CONFIRMAR o LIBERAR. */
public class Reserva {
    private String pedidoId;
    private String sedeSolicitante;
    private String codigo;
    private int cantidad;

    public Reserva() {
    }

    public Reserva(String pedidoId, String sedeSolicitante, String codigo, int cantidad) {
        this.pedidoId = pedidoId;
        this.sedeSolicitante = sedeSolicitante;
        this.codigo = codigo;
        this.cantidad = cantidad;
    }

    public String getPedidoId() { return pedidoId; }
    public String getSedeSolicitante() { return sedeSolicitante; }
    public String getCodigo() { return codigo; }
    public int getCantidad() { return cantidad; }
}
