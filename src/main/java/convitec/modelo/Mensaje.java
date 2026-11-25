package convitec.modelo;

/**
 * Mensaje entre sedes por RabbitMQ, con un formato plano.
 * Los campos que un tipo no usa quedan en null.
 */
public class Mensaje {
    /** Valor de "para" cuando el mensaje es para todas las sedes. */
    public static final String TODOS = "TODOS";

    private TipoMensaje tipo;
    private String de;
    private String para;
    private String pedidoId;
    private String codigo;
    private Integer cantidad;
    private Boolean ok;
    private String motivo;

    public Mensaje() {
    }

    public Mensaje(TipoMensaje tipo, String de, String para) {
        this.tipo = tipo;
        this.de = de;
        this.para = para;
    }

    public TipoMensaje getTipo() { return tipo; }
    public String getDe() { return de; }
    public String getPara() { return para; }
    public String getPedidoId() { return pedidoId; }
    public String getCodigo() { return codigo; }
    public Integer getCantidad() { return cantidad; }
    public Boolean getOk() { return ok; }
    public String getMotivo() { return motivo; }

    public void setPedidoId(String pedidoId) { this.pedidoId = pedidoId; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public void setOk(Boolean ok) { this.ok = ok; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    /** True si esta sede debe procesar el mensaje (va para ella o para todas). */
    public boolean esPara(String codigoSede) {
        return TODOS.equals(para) || (para != null && para.equals(codigoSede));
    }
}
