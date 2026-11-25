package convitec.modelo;

/** Producto del catálogo común de ConviTEC. El código es único. */
public class Producto {
    private String codigo;
    private String nombre;
    private String descripcion;
    private String categoria;
    private double precio;

    public Producto() {
    }

    public Producto(String codigo, String nombre, String descripcion, String categoria, double precio) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.categoria = categoria;
        this.precio = precio;
    }

    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public String getCategoria() { return categoria; }
    public double getPrecio() { return precio; }

    @Override
    public String toString() {
        return codigo + " - " + nombre;
    }
}
