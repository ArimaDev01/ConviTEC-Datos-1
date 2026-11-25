package convitec.modelo;

/** Respuesta al registrar un pedido: si se aceptó, el mensaje para el dependiente y el pedido. */
public class ResultadoPedido {
    private boolean ok;
    private String mensaje;
    private Pedido pedido;

    public ResultadoPedido() {
    }

    public ResultadoPedido(boolean ok, String mensaje, Pedido pedido) {
        this.ok = ok;
        this.mensaje = mensaje;
        this.pedido = pedido;
    }

    public static ResultadoPedido aceptado(Pedido pedido) {
        return new ResultadoPedido(true, "Pedido " + pedido.getId() + " aceptado", pedido);
    }

    public static ResultadoPedido rechazado(String motivo, Pedido pedido) {
        return new ResultadoPedido(false, motivo, pedido);
    }

    public boolean isOk() { return ok; }
    public String getMensaje() { return mensaje; }
    public Pedido getPedido() { return pedido; }
}
