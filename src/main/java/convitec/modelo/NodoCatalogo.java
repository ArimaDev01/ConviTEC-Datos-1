package convitec.modelo;

/**
 * Lo que guarda el árbol AVL del catálogo: un producto y su existencia en cada sede.
 * La casilla de la sede propia es el inventario local; las demás son la copia
 * del inventario global, que solo se actualiza por mensajes.
 */
public class NodoCatalogo {
    private Producto producto;
    private int[] existencias;

    public NodoCatalogo() {
    }

    /** Crea el nodo con existencia 0 en todas las sedes. */
    public NodoCatalogo(Producto producto, int cantidadSedes) {
        if (cantidadSedes <= 0) {
            throw new IllegalArgumentException("La cantidad de sedes debe ser mayor que 0");
        }
        this.producto = producto;
        this.existencias = new int[cantidadSedes];
    }

    public Producto getProducto() { return producto; }

    public String getCodigo() { return producto.getCodigo(); }

    public int getExistencia(int indiceSede) {
        validarIndice(indiceSede);
        return existencias[indiceSede];
    }

    public void setExistencia(int indiceSede, int cantidad) {
        validarIndice(indiceSede);
        if (cantidad < 0) {
            throw new IllegalArgumentException("La existencia no puede ser negativa");
        }
        existencias[indiceSede] = cantidad;
    }

    /** Suma de la existencia en todas las sedes. */
    public int getExistenciaTotal() {
        int total = 0;
        for (int cantidad : existencias) {
            total += cantidad;
        }
        return total;
    }

    public int getCantidadSedes() { return existencias.length; }

    /** Copia del arreglo, para que nadie lo modifique desde afuera. */
    public int[] getExistencias() {
        int[] copia = new int[existencias.length];
        for (int i = 0; i < existencias.length; i++) {
            copia[i] = existencias[i];
        }
        return copia;
    }

    private void validarIndice(int indiceSede) {
        if (indiceSede < 0 || indiceSede >= existencias.length) {
            throw new IndexOutOfBoundsException("Sede inexistente: " + indiceSede);
        }
    }
}
