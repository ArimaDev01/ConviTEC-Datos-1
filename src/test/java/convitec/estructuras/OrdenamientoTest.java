package convitec.estructuras;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrdenamientoTest {

    private static final Comparador<Integer> ASCENDENTE = (a, b) -> Integer.compare(a, b);

    @Test
    void arregloVacioYDeUnElementoNoFallan() {
        Integer[] vacio = {};
        Ordenamiento.mergeSort(vacio, ASCENDENTE);
        assertEquals(0, vacio.length);

        Integer[] uno = {7};
        Ordenamiento.mergeSort(uno, ASCENDENTE);
        assertArrayEquals(new Integer[]{7}, uno);
    }

    @Test
    void ordenaDesordenado() {
        Integer[] numeros = {5, 2, 9, 1, 7, 3};
        Ordenamiento.mergeSort(numeros, ASCENDENTE);
        assertArrayEquals(new Integer[]{1, 2, 3, 5, 7, 9}, numeros);
    }

    @Test
    void yaOrdenadoYAlReves() {
        Integer[] ordenado = {1, 2, 3, 4, 5};
        Ordenamiento.mergeSort(ordenado, ASCENDENTE);
        assertArrayEquals(new Integer[]{1, 2, 3, 4, 5}, ordenado);

        Integer[] alReves = {5, 4, 3, 2, 1};
        Ordenamiento.mergeSort(alReves, ASCENDENTE);
        assertArrayEquals(new Integer[]{1, 2, 3, 4, 5}, alReves);
    }

    @Test
    void conRepetidos() {
        Integer[] numeros = {3, 1, 3, 2, 1, 3};
        Ordenamiento.mergeSort(numeros, ASCENDENTE);
        assertArrayEquals(new Integer[]{1, 1, 2, 3, 3, 3}, numeros);
    }

    @Test
    void descendenteInvirtiendoElComparador() {
        Integer[] numeros = {2, 9, 4};
        Ordenamiento.mergeSort(numeros, (a, b) -> Integer.compare(b, a));
        assertArrayEquals(new Integer[]{9, 4, 2}, numeros);
    }

    @Test
    void esEstableLosEmpatesConservanSuOrden() {
        // Ordenar por la letra; los que tienen la misma letra deben quedar en su orden original.
        String[] datos = {"B1", "A1", "B2", "A2", "B3"};
        Ordenamiento.mergeSort(datos, (a, b) -> Character.compare(a.charAt(0), b.charAt(0)));
        assertArrayEquals(new String[]{"A1", "A2", "B1", "B2", "B3"}, datos);
    }

    @Test
    void muchosElementos() {
        Integer[] numeros = new Integer[1000];
        for (int i = 0; i < numeros.length; i++) {
            numeros[i] = (i * 7919) % 1000;
        }
        Ordenamiento.mergeSort(numeros, ASCENDENTE);
        for (int i = 1; i < numeros.length; i++) {
            assertTrue(numeros[i - 1] <= numeros[i]);
        }
    }

    @Test
    void rechazaNull() {
        assertThrows(IllegalArgumentException.class, () -> Ordenamiento.mergeSort(null, ASCENDENTE));
        assertThrows(IllegalArgumentException.class, () -> Ordenamiento.mergeSort(new Integer[]{1}, null));
    }
}
