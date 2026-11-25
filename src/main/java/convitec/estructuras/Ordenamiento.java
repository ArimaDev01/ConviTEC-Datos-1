package convitec.estructuras;

/**
 * Algoritmos de ordenamiento propios.
 *
 * Merge sort: divide el arreglo en dos mitades, ordena cada una (recursivamente)
 * y luego las mezcla en orden. Siempre es O(n log n), también en el peor caso,
 * y es estable: si dos elementos empatan, conservan el orden que tenían.
 */
public final class Ordenamiento {

    private Ordenamiento() {
    }

    /** Ordena el arreglo en el mismo lugar, de menor a mayor según el comparador. */
    public static <T> void mergeSort(T[] arreglo, Comparador<T> comparador) {
        if (arreglo == null || comparador == null) {
            throw new IllegalArgumentException("El arreglo y el comparador no pueden ser null");
        }
        if (arreglo.length < 2) {
            return;
        }
        Object[] auxiliar = new Object[arreglo.length];
        ordenar(arreglo, auxiliar, 0, arreglo.length - 1, comparador);
    }

    /** Ordena el tramo [inicio, fin] del arreglo. */
    private static <T> void ordenar(T[] arreglo, Object[] auxiliar, int inicio, int fin, Comparador<T> comparador) {
        if (inicio >= fin) {
            return;
        }
        int medio = (inicio + fin) / 2;
        ordenar(arreglo, auxiliar, inicio, medio, comparador);
        ordenar(arreglo, auxiliar, medio + 1, fin, comparador);
        mezclar(arreglo, auxiliar, inicio, medio, fin, comparador);
    }

    /** Mezcla las mitades ya ordenadas [inicio, medio] y [medio+1, fin]. */
    @SuppressWarnings("unchecked")
    private static <T> void mezclar(T[] arreglo, Object[] auxiliar, int inicio, int medio, int fin,
                                    Comparador<T> comparador) {
        for (int k = inicio; k <= fin; k++) {
            auxiliar[k] = arreglo[k];
        }
        int i = inicio;
        int j = medio + 1;
        for (int k = inicio; k <= fin; k++) {
            if (i > medio) {
                arreglo[k] = (T) auxiliar[j++];
            } else if (j > fin) {
                arreglo[k] = (T) auxiliar[i++];
            } else if (comparador.comparar((T) auxiliar[j], (T) auxiliar[i]) < 0) {
                // Solo se toma el de la derecha si es estrictamente menor: así es estable.
                arreglo[k] = (T) auxiliar[j++];
            } else {
                arreglo[k] = (T) auxiliar[i++];
            }
        }
    }
}
