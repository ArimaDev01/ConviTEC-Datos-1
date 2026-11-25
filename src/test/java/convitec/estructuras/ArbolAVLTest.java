package convitec.estructuras;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArbolAVLTest {

    private ArbolAVL<Integer> arbol;

    @BeforeEach
    void crearArbol() {
        arbol = new ArbolAVL<>();
    }

    private void insertar(String... claves) {
        for (String clave : claves) {
            arbol.insertar(clave, clave.length());
        }
    }

    @Test
    void arbolNuevoEstaVacio() {
        assertTrue(arbol.estaVacio());
        assertEquals(0, arbol.tamano());
        assertEquals(0, arbol.altura());
        assertNull(arbol.buscar("P01"));
        assertFalse(arbol.contiene("P01"));
        assertEquals(0, arbol.enOrden(new Integer[0]).length);
        assertTrue(arbol.esAVLValido());
    }

    @Test
    void insertarYBuscar() {
        arbol.insertar("P02", 20);
        arbol.insertar("P01", 10);
        arbol.insertar("P03", 30);
        assertEquals(3, arbol.tamano());
        assertEquals(10, arbol.buscar("P01"));
        assertEquals(30, arbol.buscar("P03"));
        assertTrue(arbol.contiene("P02"));
    }

    @Test
    void buscarInexistenteDevuelveNull() {
        arbol.insertar("P01", 10);
        assertNull(arbol.buscar("P99"));
        assertNull(arbol.buscar(null));
        assertFalse(arbol.contiene("P99"));
    }

    @Test
    void claveDuplicadaReemplazaElValorSinCrecer() {
        arbol.insertar("P01", 10);
        arbol.insertar("P01", 99);
        assertEquals(1, arbol.tamano());
        assertEquals(99, arbol.buscar("P01"));
    }

    @Test
    void noAceptaNull() {
        assertThrows(IllegalArgumentException.class, () -> arbol.insertar(null, 1));
        assertThrows(IllegalArgumentException.class, () -> arbol.insertar("P01", null));
    }

    // Los 4 casos de rotación: con 3 inserciones el árbol debe quedar con "B" en la raíz.

    @Test
    void casoIzquierdaIzquierdaRotaADerecha() {
        insertar("C", "B", "A");
        assertEquals("B", arbol.claveRaiz());
        assertEquals(2, arbol.altura());
        assertTrue(arbol.esAVLValido());
    }

    @Test
    void casoDerechaDerechaRotaAIzquierda() {
        insertar("A", "B", "C");
        assertEquals("B", arbol.claveRaiz());
        assertEquals(2, arbol.altura());
        assertTrue(arbol.esAVLValido());
    }

    @Test
    void casoIzquierdaDerechaHaceRotacionDoble() {
        insertar("C", "A", "B");
        assertEquals("B", arbol.claveRaiz());
        assertEquals(2, arbol.altura());
        assertTrue(arbol.esAVLValido());
    }

    @Test
    void casoDerechaIzquierdaHaceRotacionDoble() {
        insertar("A", "C", "B");
        assertEquals("B", arbol.claveRaiz());
        assertEquals(2, arbol.altura());
        assertTrue(arbol.esAVLValido());
    }

    @Test
    void insertarEnOrdenAscendenteSigueBalanceado() {
        // Un BST normal quedaría con altura 1000; el AVL debe quedar cerca de log2(1000) ≈ 10.
        for (int i = 0; i < 1000; i++) {
            arbol.insertar(String.format("P%04d", i), i);
        }
        assertEquals(1000, arbol.tamano());
        assertTrue(arbol.altura() <= 14, "altura " + arbol.altura());
        assertTrue(arbol.esAVLValido());
        assertEquals(500, arbol.buscar("P0500"));
    }

    @Test
    void insertarEnOrdenDescendenteSigueBalanceado() {
        for (int i = 999; i >= 0; i--) {
            arbol.insertar(String.format("P%04d", i), i);
        }
        assertTrue(arbol.altura() <= 14, "altura " + arbol.altura());
        assertTrue(arbol.esAVLValido());
    }

    @Test
    void enOrdenDevuelveLosValoresOrdenadosPorClave() {
        arbol.insertar("P03", 3);
        arbol.insertar("P01", 1);
        arbol.insertar("P05", 5);
        arbol.insertar("P02", 2);
        arbol.insertar("P04", 4);
        Integer[] valores = arbol.enOrden(new Integer[0]);
        assertEquals(Integer[].class, valores.getClass());
        assertArrayEquals(new Integer[]{1, 2, 3, 4, 5}, valores);
    }
}
