package convitec.estructuras;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ColaTest {

    private Cola<String> cola;

    @BeforeEach
    void crearCola() {
        cola = new Cola<>();
    }

    @Test
    void colaNuevaEstaVacia() {
        assertTrue(cola.estaVacia());
        assertEquals(0, cola.tamano());
        assertEquals(0, cola.toArray(new String[0]).length);
    }

    @Test
    void desencolarOVerUnaColaVaciaLanzaExcepcion() {
        assertThrows(IllegalStateException.class, () -> cola.desencolar());
        assertThrows(IllegalStateException.class, () -> cola.verFrente());
    }

    @Test
    void saleEnElMismoOrdenEnQueEntra() {
        cola.encolar("A");
        cola.encolar("B");
        cola.encolar("C");
        assertEquals("A", cola.desencolar());
        assertEquals("B", cola.desencolar());
        assertEquals("C", cola.desencolar());
        assertTrue(cola.estaVacia());
    }

    @Test
    void verFrenteNoSacaElElemento() {
        cola.encolar("A");
        cola.encolar("B");
        assertEquals("A", cola.verFrente());
        assertEquals(2, cola.tamano());
        assertEquals("A", cola.desencolar());
    }

    @Test
    void vaciarYVolverAUsar() {
        cola.encolar("A");
        cola.desencolar();
        assertTrue(cola.estaVacia());
        assertThrows(IllegalStateException.class, () -> cola.desencolar());
        cola.encolar("B");
        assertEquals("B", cola.verFrente());
        assertEquals(1, cola.tamano());
    }

    @Test
    void encolarYDesencolarIntercalados() {
        cola.encolar("A");
        cola.encolar("B");
        assertEquals("A", cola.desencolar());
        cola.encolar("C");
        assertArrayEquals(new String[]{"B", "C"}, cola.toArray(new String[0]));
    }

    @Test
    void aceptaDuplicados() {
        cola.encolar("A");
        cola.encolar("A");
        assertEquals(2, cola.tamano());
        assertEquals("A", cola.desencolar());
        assertEquals("A", cola.desencolar());
    }

    @Test
    void noAceptaNull() {
        assertThrows(IllegalArgumentException.class, () -> cola.encolar(null));
    }

    @Test
    void toArrayNoModificaLaCola() {
        cola.encolar("A");
        cola.encolar("B");
        assertArrayEquals(new String[]{"A", "B"}, cola.toArray(new String[0]));
        assertEquals(2, cola.tamano());
        assertEquals("A", cola.verFrente());
    }
}
