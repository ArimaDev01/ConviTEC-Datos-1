package convitec.estructuras;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ListaEnlazadaTest {

    private ListaEnlazada<String> lista;

    @BeforeEach
    void crearLista() {
        lista = new ListaEnlazada<>();
    }

    private void llenar(String... datos) {
        for (String dato : datos) {
            lista.agregar(dato);
        }
    }

    @Test
    void listaNuevaEstaVacia() {
        assertTrue(lista.estaVacia());
        assertEquals(0, lista.tamano());
        assertEquals(0, lista.toArray(new String[0]).length);
        assertFalse(lista.contiene("A"));
        assertFalse(lista.eliminar("A"));
    }

    @Test
    void agregarMantieneElOrden() {
        llenar("A", "B", "C");
        assertFalse(lista.estaVacia());
        assertEquals(3, lista.tamano());
        assertEquals("A", lista.obtener(0));
        assertEquals("C", lista.obtener(2));
        assertArrayEquals(new String[]{"A", "B", "C"}, lista.toArray(new String[0]));
    }

    @Test
    void obtenerFueraDeRangoLanzaExcepcion() {
        llenar("A");
        assertThrows(IndexOutOfBoundsException.class, () -> lista.obtener(1));
        assertThrows(IndexOutOfBoundsException.class, () -> lista.obtener(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> new ListaEnlazada<String>().obtener(0));
    }

    @Test
    void noAceptaNull() {
        assertThrows(IllegalArgumentException.class, () -> lista.agregar(null));
    }

    @Test
    void eliminarElPrimero() {
        llenar("A", "B", "C");
        assertTrue(lista.eliminar("A"));
        assertArrayEquals(new String[]{"B", "C"}, lista.toArray(new String[0]));
    }

    @Test
    void eliminarElUltimoYLuegoAgregar() {
        llenar("A", "B", "C");
        assertTrue(lista.eliminar("C"));
        lista.agregar("D");
        assertArrayEquals(new String[]{"A", "B", "D"}, lista.toArray(new String[0]));
    }

    @Test
    void eliminarDelMedio() {
        llenar("A", "B", "C");
        assertTrue(lista.eliminar("B"));
        assertEquals(2, lista.tamano());
        assertArrayEquals(new String[]{"A", "C"}, lista.toArray(new String[0]));
    }

    @Test
    void eliminarElUnicoDejaLaListaVaciaYUsable() {
        llenar("A");
        assertTrue(lista.eliminar("A"));
        assertTrue(lista.estaVacia());
        lista.agregar("B");
        assertArrayEquals(new String[]{"B"}, lista.toArray(new String[0]));
    }

    @Test
    void eliminarUnoQueNoExiste() {
        llenar("A", "B");
        assertFalse(lista.eliminar("Z"));
        assertEquals(2, lista.tamano());
    }

    @Test
    void conDuplicadosEliminaSoloLaPrimeraAparicion() {
        llenar("A", "B", "A");
        assertTrue(lista.eliminar("A"));
        assertArrayEquals(new String[]{"B", "A"}, lista.toArray(new String[0]));
        assertTrue(lista.contiene("A"));
    }

    @Test
    void toArrayDevuelveArregloDelTipoCorrecto() {
        ListaEnlazada<Integer> numeros = new ListaEnlazada<>();
        numeros.agregar(5);
        numeros.agregar(7);
        Integer[] arreglo = numeros.toArray(new Integer[0]);
        assertEquals(Integer[].class, arreglo.getClass());
        assertArrayEquals(new Integer[]{5, 7}, arreglo);
    }
}
