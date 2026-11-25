package convitec.modelo;

/**
 * Una de las cuatro sedes. La ubicación es el nombre del lugar en el grafo
 * de rutas (por ejemplo "Alajuela"); el puerto es donde corre su servidor web.
 */
public class Sede {
    private String codigo;
    private String nombre;
    private String ubicacion;
    private int puerto;

    public Sede() {
    }

    public Sede(String codigo, String nombre, String ubicacion, int puerto) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.ubicacion = ubicacion;
        this.puerto = puerto;
    }

    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public String getUbicacion() { return ubicacion; }
    public int getPuerto() { return puerto; }

    @Override
    public String toString() {
        return codigo + " (" + nombre + ")";
    }
}
