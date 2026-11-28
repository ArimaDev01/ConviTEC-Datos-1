package convitec.grafo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DijkstraTest {

    @TempDir
    Path temporales;

    private Grafo cargarReal() {
        return Grafo.cargar(Path.of("config", "rutas.json").toString());
    }

    private Grafo cargarTemporal(String contenido) throws IOException {
        Path archivo = temporales.resolve("rutas.json");
        Files.writeString(archivo, contenido, StandardCharsets.UTF_8);
        return Grafo.cargar(archivo.toString());
    }

    private void comprobarRuta(Grafo grafo, ResultadoDijkstra resultado, String origen,
                               String destino, int minutos, String... caminoEsperado) {
        assertEquals(minutos, resultado.distancia(destino), origen + " -> " + destino);
        assertArrayEquals(caminoEsperado, resultado.camino(destino));
        comprobarCaminoValido(grafo, resultado, origen, destino);
    }

    /** Comprueba el recorrido con la matriz, sin volver a implementar Dijkstra como referencia. */
    private void comprobarCaminoValido(Grafo grafo, ResultadoDijkstra resultado,
                                      String origen, String destino) {
        assertTrue(resultado.alcanzable(destino), origen + " -> " + destino);
        String[] camino = resultado.camino(destino);
        int cantidad = grafo.lugares().length;
        assertTrue(camino.length > 0 && camino.length <= cantidad);
        assertEquals(origen, camino[0]);
        assertEquals(destino, camino[camino.length - 1]);

        boolean[] aparece = new boolean[cantidad];
        long suma = 0;
        for (int i = 0; i < camino.length; i++) {
            int actual = grafo.indiceDe(camino[i]);
            assertTrue(actual >= 0, "Ubicación ajena al grafo: " + camino[i]);
            assertFalse(aparece[actual], "El camino mínimo no debe repetir ubicaciones");
            aparece[actual] = true;
            if (i > 0) {
                int anterior = grafo.indiceDe(camino[i - 1]);
                int peso = grafo.peso(anterior, actual);
                assertTrue(peso > 0, "El camino debe utilizar una conexión existente");
                // También la comprobación debe evitar un desbordamiento al sumar pesos.
                suma += peso;
            }
        }
        assertEquals((long) resultado.distancia(destino), suma,
                "La suma de las aristas debe coincidir con la distancia calculada");
    }

    private void comprobarInalcanzable(ResultadoDijkstra resultado, String destino) {
        assertFalse(resultado.alcanzable(destino));
        assertEquals(ResultadoDijkstra.INALCANZABLE, resultado.distancia(destino));
        assertArrayEquals(new String[0], resultado.camino(destino));
    }

    @Test
    void alajuelaAParaisoTieneCosto95YElCaminoEsperado() {
        Grafo grafo = cargarReal();
        ResultadoDijkstra resultado = Dijkstra.calcular(grafo, "Alajuela");
        comprobarRuta(grafo, resultado, "Alajuela", "Paraíso", 95,
                "Alajuela", "San José", "Cartago", "Paraíso");
    }

    @Test
    void alajuelaAHerediaTieneCosto20YCaminoDirecto() {
        Grafo grafo = cargarReal();
        ResultadoDijkstra resultado = Dijkstra.calcular(grafo, "Alajuela");
        comprobarRuta(grafo, resultado, "Alajuela", "Heredia", 20, "Alajuela", "Heredia");
    }

    @Test
    void alajuelaAOrotinaTieneCosto55YCaminoDirecto() {
        Grafo grafo = cargarReal();
        ResultadoDijkstra resultado = Dijkstra.calcular(grafo, "Alajuela");
        comprobarRuta(grafo, resultado, "Alajuela", "Orotina", 55, "Alajuela", "Orotina");
    }

    @Test
    void liberiaACanasTieneCosto65YCaminoDirecto() {
        Grafo grafo = cargarReal();
        ResultadoDijkstra resultado = Dijkstra.calcular(grafo, "Liberia");
        comprobarRuta(grafo, resultado, "Liberia", "Cañas", 65, "Liberia", "Cañas");
    }

    @Test
    void sanJoseACartagoTieneCosto45YCaminoDirecto() {
        Grafo grafo = cargarReal();
        ResultadoDijkstra resultado = Dijkstra.calcular(grafo, "San José");
        comprobarRuta(grafo, resultado, "San José", "Cartago", 45, "San José", "Cartago");
    }

    @Test
    void canasEsInalcanzableDesdeSanJose() {
        comprobarInalcanzable(Dijkstra.calcular(cargarReal(), "San José"), "Cañas");
    }

    @Test
    void liberiaEsInalcanzableDesdeAlajuela() {
        comprobarInalcanzable(Dijkstra.calcular(cargarReal(), "Alajuela"), "Liberia");
    }

    @Test
    void limonEsInalcanzableDesdeLiberia() {
        comprobarInalcanzable(Dijkstra.calcular(cargarReal(), "Liberia"), "Limón");
    }

    @Test
    void cadaOrigenTieneDistanciaCeroYCaminoDeUnaUbicacionHaciaSiMismo() {
        Grafo grafo = cargarReal();
        for (String origen : grafo.lugares()) {
            ResultadoDijkstra resultado = Dijkstra.calcular(grafo, origen);
            comprobarRuta(grafo, resultado, origen, origen, 0, origen);
        }
    }

    @Test
    void seleccionaElCaminoIndirectoDe50EnLugarDelDirectoDe100() throws IOException {
        Grafo grafo = cargarTemporal("""
                [
                  {"origen": "A", "destino": "B", "tiempo": 100},
                  {"origen": "A", "destino": "C", "tiempo": 20},
                  {"origen": "C", "destino": "B", "tiempo": 30}
                ]
                """);
        ResultadoDijkstra resultado = Dijkstra.calcular(grafo, "A");
        comprobarRuta(grafo, resultado, "A", "B", 50, "A", "C", "B");
    }

    @Test
    void origenInexistenteLanzaExcepcionDeArgumento() {
        Grafo grafo = cargarReal();
        assertThrows(IllegalArgumentException.class, () -> Dijkstra.calcular(grafo, "Inexistente"));
    }

    @Test
    void grafoNuloLanzaExcepcionDeArgumento() {
        assertThrows(IllegalArgumentException.class, () -> Dijkstra.calcular(null, "Alajuela"));
    }

    @Test
    void origenNuloLanzaExcepcionDeArgumento() {
        Grafo grafo = cargarReal();
        assertThrows(IllegalArgumentException.class, () -> Dijkstra.calcular(grafo, null));
    }

    @Test
    void origenVacioOEnBlancoLanzaExcepcionDeArgumento() {
        Grafo grafo = cargarReal();
        assertThrows(IllegalArgumentException.class, () -> Dijkstra.calcular(grafo, ""));
        assertThrows(IllegalArgumentException.class, () -> Dijkstra.calcular(grafo, "   "));
    }

    @Test
    void destinoInexistenteRespetaElContratoDeInalcanzable() {
        comprobarInalcanzable(Dijkstra.calcular(cargarReal(), "Alajuela"), "Inexistente");
    }

    @Test
    void destinoNuloRespetaElContratoDeInalcanzable() {
        comprobarInalcanzable(Dijkstra.calcular(cargarReal(), "Alajuela"), null);
    }

    @Test
    void destinoVacioOEnBlancoRespetaElContratoDeInalcanzable() {
        ResultadoDijkstra resultado = Dijkstra.calcular(cargarReal(), "Alajuela");
        comprobarInalcanzable(resultado, "");
        comprobarInalcanzable(resultado, "   ");
    }

    @Test
    void caminosEmpatadosProcesanPrimeroElIndiceMenorSegunElContrato() throws IOException {
        Grafo grafo = cargarTemporal("""
                [
                  {"origen": "A", "destino": "B", "tiempo": 1},
                  {"origen": "A", "destino": "C", "tiempo": 1},
                  {"origen": "B", "destino": "D", "tiempo": 1},
                  {"origen": "C", "destino": "D", "tiempo": 1}
                ]
                """);
        assertTrue(grafo.indiceDe("B") < grafo.indiceDe("C"));
        ResultadoDijkstra resultado = Dijkstra.calcular(grafo, "A");
        // Ambos caminos cuestan 2; la regla documentada permite esperar A, B, D.
        comprobarRuta(grafo, resultado, "A", "D", 2, "A", "B", "D");
    }

    @Test
    void unCaminoEmpatadoNoReemplazaElPredecesorDescubiertoPrimero() throws IOException {
        Grafo grafo = cargarTemporal("""
                [
                  {"origen": "A", "destino": "D", "tiempo": 2},
                  {"origen": "A", "destino": "B", "tiempo": 1},
                  {"origen": "B", "destino": "D", "tiempo": 1}
                ]
                """);
        ResultadoDijkstra resultado = Dijkstra.calcular(grafo, "A");
        comprobarRuta(grafo, resultado, "A", "D", 2, "A", "D");
    }

    @Test
    void grafoTemporalDesconectadoSoloPermiteRecorrerElComponenteDelOrigen() throws IOException {
        Grafo grafo = cargarTemporal("""
                [
                  {"origen": "A", "destino": "B", "tiempo": 4},
                  {"origen": "B", "destino": "C", "tiempo": 6},
                  {"origen": "X", "destino": "Y", "tiempo": 2}
                ]
                """);
        ResultadoDijkstra desdeA = Dijkstra.calcular(grafo, "A");
        comprobarRuta(grafo, desdeA, "A", "C", 10, "A", "B", "C");
        comprobarInalcanzable(desdeA, "X");
        comprobarInalcanzable(desdeA, "Y");
        ResultadoDijkstra desdeX = Dijkstra.calcular(grafo, "X");
        comprobarRuta(grafo, desdeX, "X", "Y", 2, "X", "Y");
        comprobarInalcanzable(desdeX, "A");
    }

    @Test
    void admiteUnaSumaExactamenteIgualAIntegerMaxValue() throws IOException {
        Grafo grafo = cargarTemporal("""
                [
                  {"origen": "A", "destino": "B", "tiempo": 2147483640},
                  {"origen": "B", "destino": "C", "tiempo": 7}
                ]
                """);
        ResultadoDijkstra resultado = Dijkstra.calcular(grafo, "A");
        comprobarRuta(grafo, resultado, "A", "C", Integer.MAX_VALUE, "A", "B", "C");
    }

    @Test
    void distanciaMinimaSuperiorAlRangoIntLanzaExcepcionAritmetica() throws IOException {
        Grafo grafo = cargarTemporal("""
                [
                  {"origen": "A", "destino": "B", "tiempo": 2147483647},
                  {"origen": "B", "destino": "C", "tiempo": 1}
                ]
                """);
        assertThrows(ArithmeticException.class, () -> Dijkstra.calcular(grafo, "A"));
    }

    @Test
    void candidatoDesbordadoNoImpideEncontrarOtraRutaRepresentable() throws IOException {
        Grafo grafo = cargarTemporal("""
                [
                  {"origen": "A", "destino": "B", "tiempo": 2147483640},
                  {"origen": "B", "destino": "C", "tiempo": 100},
                  {"origen": "A", "destino": "D", "tiempo": 2147483641},
                  {"origen": "D", "destino": "C", "tiempo": 1}
                ]
                """);
        ResultadoDijkstra resultado = Dijkstra.calcular(grafo, "A");
        comprobarRuta(grafo, resultado, "A", "C", 2147483642, "A", "D", "C");
    }

    @Test
    void sumasGrandesNoSeConviertenEnDistanciasNegativas() throws IOException {
        Grafo grafo = cargarTemporal("""
                [
                  {"origen": "A", "destino": "B", "tiempo": 1500000000},
                  {"origen": "A", "destino": "C", "tiempo": 1600000000},
                  {"origen": "B", "destino": "C", "tiempo": 1500000000}
                ]
                """);
        ResultadoDijkstra resultado = Dijkstra.calcular(grafo, "A");
        comprobarRuta(grafo, resultado, "A", "B", 1500000000, "A", "B");
        comprobarRuta(grafo, resultado, "A", "C", 1600000000, "A", "C");
    }

    @Test
    void distanciasExcesivasEnOtroComponenteNoAfectanElCalculo() throws IOException {
        Grafo grafo = cargarTemporal("""
                [
                  {"origen": "A", "destino": "B", "tiempo": 2},
                  {"origen": "X", "destino": "Y", "tiempo": 2147483647},
                  {"origen": "Y", "destino": "Z", "tiempo": 1}
                ]
                """);
        ResultadoDijkstra resultado = Dijkstra.calcular(grafo, "A");
        comprobarRuta(grafo, resultado, "A", "B", 2, "A", "B");
        comprobarInalcanzable(resultado, "X");
        comprobarInalcanzable(resultado, "Y");
        comprobarInalcanzable(resultado, "Z");
    }

    @Test
    void funcionaConUnGrafoDeDosVerticesEnAmbasDirecciones() throws IOException {
        Grafo grafo = cargarTemporal("""
                [{"origen": "A", "destino": "B", "tiempo": 9}]
                """);
        assertEquals(2, grafo.lugares().length);
        comprobarRuta(grafo, Dijkstra.calcular(grafo, "A"), "A", "B", 9, "A", "B");
        comprobarRuta(grafo, Dijkstra.calcular(grafo, "B"), "B", "A", 9, "B", "A");
    }

    @Test
    void funcionaConUnaCadenaDeSieteVertices() throws IOException {
        Grafo grafo = cargarTemporal("""
                [
                  {"origen": "N0", "destino": "N1", "tiempo": 1},
                  {"origen": "N1", "destino": "N2", "tiempo": 2},
                  {"origen": "N2", "destino": "N3", "tiempo": 3},
                  {"origen": "N3", "destino": "N4", "tiempo": 4},
                  {"origen": "N4", "destino": "N5", "tiempo": 5},
                  {"origen": "N5", "destino": "N6", "tiempo": 6}
                ]
                """);
        assertEquals(7, grafo.lugares().length);
        comprobarRuta(grafo, Dijkstra.calcular(grafo, "N0"), "N0", "N6", 21,
                "N0", "N1", "N2", "N3", "N4", "N5", "N6");
        comprobarRuta(grafo, Dijkstra.calcular(grafo, "N6"), "N6", "N0", 21,
                "N6", "N5", "N4", "N3", "N2", "N1", "N0");
    }

    @Test
    void ejecucionesDesdeDistintosOrigenesNoAlteranResultadosAnteriores() {
        Grafo grafo = cargarReal();
        ResultadoDijkstra alajuela = Dijkstra.calcular(grafo, "Alajuela");
        ResultadoDijkstra liberia = Dijkstra.calcular(grafo, "Liberia");
        ResultadoDijkstra sanJose = Dijkstra.calcular(grafo, "San José");
        comprobarRuta(grafo, alajuela, "Alajuela", "Paraíso", 95,
                "Alajuela", "San José", "Cartago", "Paraíso");
        comprobarRuta(grafo, liberia, "Liberia", "Cañas", 65, "Liberia", "Cañas");
        comprobarRuta(grafo, sanJose, "San José", "Cartago", 45, "San José", "Cartago");
        comprobarInalcanzable(alajuela, "Cañas");
        comprobarInalcanzable(liberia, "Paraíso");
        ResultadoDijkstra repetido = Dijkstra.calcular(grafo, "Alajuela");
        comprobarRuta(grafo, repetido, "Alajuela", "Paraíso", 95,
                "Alajuela", "San José", "Cartago", "Paraíso");
        comprobarRuta(grafo, liberia, "Liberia", "Cañas", 65, "Liberia", "Cañas");
    }

    @Test
    void ejecucionesEnGrafosDistintosNoCompartenResultados() throws IOException {
        Grafo primero = cargarTemporal("""
                [{"origen": "A", "destino": "B", "tiempo": 4}]
                """);
        ResultadoDijkstra desdeA = Dijkstra.calcular(primero, "A");
        Grafo segundo = cargarTemporal("""
                [{"origen": "P", "destino": "Q", "tiempo": 8}]
                """);
        ResultadoDijkstra desdeP = Dijkstra.calcular(segundo, "P");
        comprobarRuta(primero, desdeA, "A", "B", 4, "A", "B");
        comprobarRuta(segundo, desdeP, "P", "Q", 8, "P", "Q");
        comprobarInalcanzable(desdeA, "Q");
        comprobarInalcanzable(desdeP, "B");
    }

    @Test
    void modificarUnCaminoDevueltoNoAlteraElResultadoInterno() {
        Grafo grafo = cargarReal();
        ResultadoDijkstra resultado = Dijkstra.calcular(grafo, "Alajuela");
        String[] copia = resultado.camino("Paraíso");
        copia[0] = "Alterado";
        copia[1] = null;
        assertNotSame(copia, resultado.camino("Paraíso"));
        comprobarRuta(grafo, resultado, "Alajuela", "Paraíso", 95,
                "Alajuela", "San José", "Cartago", "Paraíso");
        comprobarRuta(grafo, resultado, "Alajuela", "San José", 35, "Alajuela", "San José");
    }

    @Test
    void todosLosCaminosRealesSonValidosYRespetanLosDosComponentes() {
        Grafo grafo = cargarReal();
        String[] lugares = grafo.lugares();
        for (String origen : lugares) {
            ResultadoDijkstra resultado = Dijkstra.calcular(grafo, origen);
            boolean origenEnComponenteAislado = origen.equals("Liberia") || origen.equals("Cañas");
            for (String destino : lugares) {
                boolean destinoEnComponenteAislado = destino.equals("Liberia") || destino.equals("Cañas");
                if (origenEnComponenteAislado == destinoEnComponenteAislado) {
                    comprobarCaminoValido(grafo, resultado, origen, destino);
                } else {
                    comprobarInalcanzable(resultado, destino);
                }
            }
        }
    }
}
