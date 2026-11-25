package convitec.estructuras;

import java.lang.reflect.Array;

/**
 * Árbol AVL propio: árbol binario de búsqueda que se mantiene balanceado.
 * Se usa para el catálogo de cada sede, con el código de producto como clave.
 *
 * Por qué AVL: la diferencia de altura entre los dos hijos de cualquier nodo es
 * como máximo 1, así que la altura es O(log n) y buscar e insertar son O(log n)
 * siempre (un BST normal se vuelve una lista si los códigos llegan en orden).
 *
 * Si se inserta una clave que ya existe, se reemplaza su valor (no hay duplicados).
 * No acepta claves ni valores null.
 */
public class ArbolAVL<V> {

    private static class Nodo<V> {
        String clave;
        V valor;
        Nodo<V> izquierdo;
        Nodo<V> derecho;
        int altura = 1;

        Nodo(String clave, V valor) {
            this.clave = clave;
            this.valor = valor;
        }
    }

    private Nodo<V> raiz;
    private int tamano;

    /** Inserta la clave con su valor, o reemplaza el valor si la clave ya existe. O(log n). */
    public void insertar(String clave, V valor) {
        if (clave == null || valor == null) {
            throw new IllegalArgumentException("El árbol no acepta claves ni valores null");
        }
        raiz = insertar(raiz, clave, valor);
    }

    private Nodo<V> insertar(Nodo<V> nodo, String clave, V valor) {
        if (nodo == null) {
            tamano++;
            return new Nodo<>(clave, valor);
        }
        int comparacion = clave.compareTo(nodo.clave);
        if (comparacion < 0) {
            nodo.izquierdo = insertar(nodo.izquierdo, clave, valor);
        } else if (comparacion > 0) {
            nodo.derecho = insertar(nodo.derecho, clave, valor);
        } else {
            nodo.valor = valor;
            return nodo;
        }
        actualizarAltura(nodo);
        return balancear(nodo);
    }

    /** Devuelve el valor de la clave, o null si no existe. O(log n). */
    public V buscar(String clave) {
        if (clave == null) {
            return null;
        }
        Nodo<V> actual = raiz;
        while (actual != null) {
            int comparacion = clave.compareTo(actual.clave);
            if (comparacion == 0) {
                return actual.valor;
            }
            actual = comparacion < 0 ? actual.izquierdo : actual.derecho;
        }
        return null;
    }

    public boolean contiene(String clave) {
        return buscar(clave) != null;
    }

    public int tamano() {
        return tamano;
    }

    public boolean estaVacio() {
        return tamano == 0;
    }

    /** Altura del árbol (0 si está vacío). */
    public int altura() {
        return altura(raiz);
    }

    /**
     * Valores ordenados por clave (recorrido en orden: izquierdo, nodo, derecho).
     * Uso: {@code NodoCatalogo[] productos = arbol.enOrden(new NodoCatalogo[0]);}
     */
    @SuppressWarnings("unchecked")
    public V[] enOrden(V[] molde) {
        V[] resultado = (V[]) Array.newInstance(molde.getClass().getComponentType(), tamano);
        llenarEnOrden(raiz, resultado, 0);
        return resultado;
    }

    /** Llena el arreglo desde la posición dada y devuelve la siguiente posición libre. */
    private int llenarEnOrden(Nodo<V> nodo, V[] resultado, int posicion) {
        if (nodo == null) {
            return posicion;
        }
        posicion = llenarEnOrden(nodo.izquierdo, resultado, posicion);
        resultado[posicion] = nodo.valor;
        posicion++;
        return llenarEnOrden(nodo.derecho, resultado, posicion);
    }

    // ---------- Balanceo ----------

    private int altura(Nodo<V> nodo) {
        return nodo == null ? 0 : nodo.altura;
    }

    private void actualizarAltura(Nodo<V> nodo) {
        nodo.altura = 1 + Math.max(altura(nodo.izquierdo), altura(nodo.derecho));
    }

    /** Factor de balance: altura izquierda menos altura derecha. En un AVL está entre -1 y 1. */
    private int factorBalance(Nodo<V> nodo) {
        return altura(nodo.izquierdo) - altura(nodo.derecho);
    }

    /** Aplica la rotación que corresponda si el nodo quedó desbalanceado (factor 2 o -2). */
    private Nodo<V> balancear(Nodo<V> nodo) {
        int factor = factorBalance(nodo);
        if (factor > 1) {
            // Cargado a la izquierda
            if (factorBalance(nodo.izquierdo) < 0) {
                // Caso izquierda-derecha: primero rotar el hijo a la izquierda
                nodo.izquierdo = rotarIzquierda(nodo.izquierdo);
            }
            // Caso izquierda-izquierda
            return rotarDerecha(nodo);
        }
        if (factor < -1) {
            // Cargado a la derecha
            if (factorBalance(nodo.derecho) > 0) {
                // Caso derecha-izquierda: primero rotar el hijo a la derecha
                nodo.derecho = rotarDerecha(nodo.derecho);
            }
            // Caso derecha-derecha
            return rotarIzquierda(nodo);
        }
        return nodo;
    }

    /**
     * Rotación simple a la derecha:
     * <pre>
     *       y            x
     *      / \          / \
     *     x   C  -->   A   y
     *    / \              / \
     *   A   B            B   C
     * </pre>
     */
    private Nodo<V> rotarDerecha(Nodo<V> y) {
        Nodo<V> x = y.izquierdo;
        y.izquierdo = x.derecho;
        x.derecho = y;
        actualizarAltura(y);
        actualizarAltura(x);
        return x;
    }

    /**
     * Rotación simple a la izquierda (espejo de la anterior):
     * <pre>
     *     x                y
     *    / \              / \
     *   A   y    -->     x   C
     *      / \          / \
     *     B   C        A   B
     * </pre>
     */
    private Nodo<V> rotarIzquierda(Nodo<V> x) {
        Nodo<V> y = x.derecho;
        x.derecho = y.izquierdo;
        y.izquierdo = x;
        actualizarAltura(x);
        actualizarAltura(y);
        return y;
    }

    // ---------- Solo para pruebas ----------

    /** Clave de la raíz (null si está vacío). Visible solo dentro del paquete, para las pruebas. */
    String claveRaiz() {
        return raiz == null ? null : raiz.clave;
    }

    /** True si todo nodo cumple orden de BST, altura correcta y factor entre -1 y 1. */
    boolean esAVLValido() {
        return validar(raiz, null, null);
    }

    private boolean validar(Nodo<V> nodo, String minimo, String maximo) {
        if (nodo == null) {
            return true;
        }
        if (minimo != null && nodo.clave.compareTo(minimo) <= 0) {
            return false;
        }
        if (maximo != null && nodo.clave.compareTo(maximo) >= 0) {
            return false;
        }
        int alturaEsperada = 1 + Math.max(altura(nodo.izquierdo), altura(nodo.derecho));
        if (nodo.altura != alturaEsperada || Math.abs(factorBalance(nodo)) > 1) {
            return false;
        }
        return validar(nodo.izquierdo, minimo, nodo.clave) && validar(nodo.derecho, nodo.clave, maximo);
    }
}
