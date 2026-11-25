package convitec.modelo;

/**
 * Una línea de un pedido o de una venta: qué producto, cuántas unidades,
 * a qué precio y qué sede lo suministra. Si un producto viene de dos sedes,
 * se guarda en dos líneas, una por sede.
 */
public class LineaPedido {
    private String codigo;
    private String nombre;
    private int cantidad;
    private double precioUnitario;
    private String sedeQueSuministra;

    public LineaPedido() {
    }

    /** Línea tal como la pide el dependiente: solo producto y cantidad. */
    public LineaPedido(String codigo, int cantidad) {
        this.codigo = codigo;
        this.cantidad = cantidad;
    }

    public LineaPedido(String codigo, String nombre, int cantidad, double precioUnitario, String sedeQueSuministra) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.sedeQueSuministra = sedeQueSuministra;
    }

    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public int getCantidad() { return cantidad; }
    public double getPrecioUnitario() { return precioUnitario; }
    public String getSedeQueSuministra() { return sedeQueSuministra; }

    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    public void setPrecioUnitario(double precioUnitario) { this.precioUnitario = precioUnitario; }
    public void setSedeQueSuministra(String sedeQueSuministra) { this.sedeQueSuministra = sedeQueSuministra; }

    public double getSubtotal() {
        return cantidad * precioUnitario;
    }
}
