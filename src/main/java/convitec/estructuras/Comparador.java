package convitec.estructuras;

/**
 * Criterio de orden propio, para no depender de las utilidades de ordenamiento del lenguaje.
 * Devuelve negativo si a va antes que b, positivo si va después y 0 si son equivalentes.
 *
 * Ejemplo (peores pedidos, de menor a mayor rendimiento):
 * {@code Comparador<Venta> porRendimiento = (a, b) -> Double.compare(a.getRendimiento(), b.getRendimiento());}
 */
public interface Comparador<T> {
    int comparar(T a, T b);
}
