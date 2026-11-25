package convitec.estructuras;

import java.lang.reflect.Array;

/**
 * Cola FIFO propia, enlazada. Se usa para los pedidos pendientes de entrega:
 * salen al recorrido en el mismo orden en que se confirmaron.
 *
 * Se encola por el final y se desencola por el frente, las dos cosas en O(1).
 * No acepta elementos null.
 */
public class Cola<T> {

    /** Cada nodo guarda un dato y apunta al que está detrás de él. */
    private static class Nodo<T> {
        T dato;
        Nodo<T> siguiente;

        Nodo(T dato) {
            this.dato = dato;
        }
    }

    private Nodo<T> frente;
    private Nodo<T> fin;
    private int tamano;

    /** Agrega el elemento al final de la cola. O(1). */
    public void encolar(T dato) {
        if (dato == null) {
            throw new IllegalArgumentException("La cola no acepta elementos null");
        }
        Nodo<T> nuevo = new Nodo<>(dato);
        if (frente == null) {
            frente = nuevo;
        } else {
            fin.siguiente = nuevo;
        }
        fin = nuevo;
        tamano++;
    }

    /** Saca y devuelve el elemento del frente. O(1). Lanza excepción si la cola está vacía. */
    public T desencolar() {
        if (frente == null) {
            throw new IllegalStateException("No se puede desencolar: la cola está vacía");
        }
        T dato = frente.dato;
        frente = frente.siguiente;
        if (frente == null) {
            fin = null;
        }
        tamano--;
        return dato;
    }

    /** Devuelve el elemento del frente sin sacarlo. Lanza excepción si la cola está vacía. */
    public T verFrente() {
        if (frente == null) {
            throw new IllegalStateException("La cola está vacía");
        }
        return frente.dato;
    }

    public int tamano() {
        return tamano;
    }

    public boolean estaVacia() {
        return tamano == 0;
    }

    /**
     * Copia los elementos, del frente al final, a un arreglo nuevo del tipo del molde.
     * No modifica la cola. Uso: {@code Pedido[] pendientes = cola.toArray(new Pedido[0]);}
     */
    @SuppressWarnings("unchecked")
    public T[] toArray(T[] molde) {
        T[] resultado = (T[]) Array.newInstance(molde.getClass().getComponentType(), tamano);
        Nodo<T> actual = frente;
        for (int i = 0; i < tamano; i++) {
            resultado[i] = actual.dato;
            actual = actual.siguiente;
        }
        return resultado;
    }
}
