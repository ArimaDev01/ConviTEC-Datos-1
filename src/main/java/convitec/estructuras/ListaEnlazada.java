package convitec.estructuras;

import java.lang.reflect.Array;

/**
 * Lista simplemente enlazada propia. Se usa para los historiales
 * (pedidos, ventas, reservas, recorridos), donde solo se agrega al final y se recorre.
 *
 * Guarda referencia al primer y al último nodo, así agregar al final es O(1).
 * No acepta elementos null.
 */
public class ListaEnlazada<T> {

    /** Cada nodo guarda un dato y apunta al siguiente. */
    private static class Nodo<T> {
        T dato;
        Nodo<T> siguiente;

        Nodo(T dato) {
            this.dato = dato;
        }
    }

    private Nodo<T> primero;
    private Nodo<T> ultimo;
    private int tamano;

    /** Agrega el elemento al final. O(1). */
    public void agregar(T dato) {
        if (dato == null) {
            throw new IllegalArgumentException("La lista no acepta elementos null");
        }
        Nodo<T> nuevo = new Nodo<>(dato);
        if (primero == null) {
            primero = nuevo;
        } else {
            ultimo.siguiente = nuevo;
        }
        ultimo = nuevo;
        tamano++;
    }

    /** Devuelve el elemento en la posición indicada (0 es el primero). O(n). */
    public T obtener(int indice) {
        if (indice < 0 || indice >= tamano) {
            throw new IndexOutOfBoundsException("Índice " + indice + " fuera de la lista de tamaño " + tamano);
        }
        Nodo<T> actual = primero;
        for (int i = 0; i < indice; i++) {
            actual = actual.siguiente;
        }
        return actual.dato;
    }

    /** True si algún elemento es igual (equals) al dado. O(n). */
    public boolean contiene(T dato) {
        Nodo<T> actual = primero;
        while (actual != null) {
            if (actual.dato.equals(dato)) {
                return true;
            }
            actual = actual.siguiente;
        }
        return false;
    }

    /** Elimina la primera aparición del elemento. Devuelve false si no estaba. O(n). */
    public boolean eliminar(T dato) {
        Nodo<T> anterior = null;
        Nodo<T> actual = primero;
        while (actual != null) {
            if (actual.dato.equals(dato)) {
                if (anterior == null) {
                    primero = actual.siguiente;
                } else {
                    anterior.siguiente = actual.siguiente;
                }
                if (actual == ultimo) {
                    ultimo = anterior;
                }
                tamano--;
                return true;
            }
            anterior = actual;
            actual = actual.siguiente;
        }
        return false;
    }

    public int tamano() {
        return tamano;
    }

    public boolean estaVacia() {
        return tamano == 0;
    }

    /**
     * Copia los elementos, en orden, a un arreglo nuevo del mismo tipo que el molde.
     * Uso: {@code Pedido[] arreglo = lista.toArray(new Pedido[0]);}
     */
    @SuppressWarnings("unchecked")
    public T[] toArray(T[] molde) {
        T[] resultado = (T[]) Array.newInstance(molde.getClass().getComponentType(), tamano);
        Nodo<T> actual = primero;
        for (int i = 0; i < tamano; i++) {
            resultado[i] = actual.dato;
            actual = actual.siguiente;
        }
        return resultado;
    }
}
