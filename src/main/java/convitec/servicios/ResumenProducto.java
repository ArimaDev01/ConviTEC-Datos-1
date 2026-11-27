package convitec.servicios;

import convitec.modelo.Producto;

/**
 * Totales de un producto en las ventas de la sede: unidades vendidas e ingresos
 * (suma de cantidad × precio unitario de cada línea donde aparece).
 */
public class ResumenProducto {
    private final String codigo;
    private final String nombre;
    private double ultimoPrecio;
    private int unidades;
    private double ingresos;

    public ResumenProducto(String codigo, String nombre) {
        this.codigo = codigo;
        this.nombre = nombre;
    }

    /** Suma una línea de venta de este producto. */
    void sumar(int cantidad, double precioUnitario) {
        unidades += cantidad;
        ingresos += cantidad * precioUnitario;
        ultimoPrecio = precioUnitario;
    }

    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public int getUnidades() { return unidades; }
    public double getIngresos() { return ingresos; }

    /** El producto con el precio de su venta más reciente (las ventas no guardan descripción ni categoría). */
    public Producto comoProducto() {
        return new Producto(codigo, nombre, "", "", ultimoPrecio);
    }
}
