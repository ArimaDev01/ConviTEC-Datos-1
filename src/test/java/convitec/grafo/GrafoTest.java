package convitec.grafo;

import com.google.gson.Gson;
import convitec.modelo.Sede;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GrafoTest {

    // Orden de primera aparición en la tabla del profesor, no obtenido del grafo.
    private static final String[] LUGARES_ESPERADOS = {
            "Liberia", "Cañas", "Puntarenas", "Orotina", "Alajuela", "San José", "Heredia",
            "Cartago", "Paraíso", "Turrialba", "Guápiles", "Siquirres", "Limón"
    };

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

    private void comprobarConexion(Grafo grafo, String origen, String destino, int tiempo) {
        int a = grafo.indiceDe(origen);
        int b = grafo.indiceDe(destino);
        assertTrue(a >= 0, "Falta la ubicación " + origen);
        assertTrue(b >= 0, "Falta la ubicación " + destino);
        assertEquals(tiempo, grafo.peso(a, b), origen + " -> " + destino);
        assertEquals(tiempo, grafo.peso(b, a), destino + " -> " + origen);
    }

    @Test
    void cargaElGrafoRealConTreceUbicacionesUnicas() {
        String[] lugares = cargarReal().lugares();
        assertEquals(13, lugares.length);
        for (int i = 0; i < lugares.length; i++) {
            assertNotNull(lugares[i]);
            assertFalse(lugares[i].isBlank());
            for (int j = i + 1; j < lugares.length; j++) {
                assertFalse(lugares[i].equals(lugares[j]), "Ubicación repetida: " + lugares[i]);
            }
        }
    }

    @Test
    void cargaExactamenteLasQuinceConexionesConLosPesosDelProfesor() {
        Grafo grafo = cargarReal();
        comprobarConexion(grafo, "Liberia", "Cañas", 65);
        comprobarConexion(grafo, "Puntarenas", "Orotina", 45);
        comprobarConexion(grafo, "Puntarenas", "Alajuela", 80);
        comprobarConexion(grafo, "Orotina", "Alajuela", 55);
        comprobarConexion(grafo, "Alajuela", "San José", 35);
        comprobarConexion(grafo, "Alajuela", "Heredia", 20);
        comprobarConexion(grafo, "San José", "Heredia", 20);
        comprobarConexion(grafo, "San José", "Cartago", 45);
        comprobarConexion(grafo, "Cartago", "Paraíso", 15);
        comprobarConexion(grafo, "Paraíso", "Turrialba", 45);
        comprobarConexion(grafo, "San José", "Guápiles", 70);
        comprobarConexion(grafo, "Guápiles", "Siquirres", 55);
        comprobarConexion(grafo, "Siquirres", "Limón", 65);
        comprobarConexion(grafo, "Turrialba", "Limón", 75);
        comprobarConexion(grafo, "Guápiles", "Turrialba", 80);

        int conexiones = 0;
        int cantidad = grafo.lugares().length;
        for (int i = 0; i < cantidad; i++) {
            // Solo el triángulo superior: no contamos dos veces una ruta bidireccional.
            for (int j = i + 1; j < cantidad; j++) {
                if (grafo.peso(i, j) != Grafo.SIN_CONEXION) {
                    conexiones++;
                }
            }
        }
        assertEquals(15, conexiones, "No deben aparecer conexiones adicionales");
    }

    @Test
    void contieneLasUbicacionesDeLasCuatroSedes() throws IOException {
        Grafo grafo = cargarReal();
        String json = Files.readString(Path.of("config", "sedes.json"), StandardCharsets.UTF_8);
        Sede[] sedes = new Gson().fromJson(json, Sede[].class);
        assertEquals(4, sedes.length);
        for (Sede sede : sedes) {
            assertTrue(grafo.indiceDe(sede.getUbicacion()) >= 0,
                    "La sede " + sede.getCodigo() + " debe existir en el grafo");
        }
    }

    @Test
    void conservaLosNombresConTildes() {
        Grafo grafo = cargarReal();
        String[] acentuados = {"Cañas", "San José", "Paraíso", "Guápiles", "Limón"};
        for (String nombre : acentuados) {
            int indice = grafo.indiceDe(nombre);
            assertTrue(indice >= 0, "No se reconoció " + nombre);
            assertEquals(nombre, grafo.nombreDe(indice));
        }
    }

    @Test
    void todaLaMatrizEsSimetrica() {
        Grafo grafo = cargarReal();
        int cantidad = grafo.lugares().length;
        for (int a = 0; a < cantidad; a++) {
            for (int b = 0; b < cantidad; b++) {
                assertEquals(grafo.peso(a, b), grafo.peso(b, a),
                        "Asimetría entre los índices " + a + " y " + b);
            }
        }
    }

    @Test
    void todaLaDiagonalTienePesoCero() {
        Grafo grafo = cargarReal();
        for (int i = 0; i < grafo.lugares().length; i++) {
            assertEquals(0, grafo.peso(i, i), grafo.nombreDe(i));
        }
    }

    @Test
    void distingueLasConexionesDirectasInexistentesDeLaDiagonal() {
        Grafo grafo = cargarReal();
        assertEquals(Grafo.SIN_CONEXION,
                grafo.peso(grafo.indiceDe("Liberia"), grafo.indiceDe("San José")));
        assertEquals(Grafo.SIN_CONEXION,
                grafo.peso(grafo.indiceDe("Cañas"), grafo.indiceDe("Alajuela")));
        // Esta ausencia es directa; los caminos indirectos se probarán con Dijkstra.
        assertEquals(Grafo.SIN_CONEXION,
                grafo.peso(grafo.indiceDe("Alajuela"), grafo.indiceDe("Paraíso")));
        assertFalse(Grafo.SIN_CONEXION == 0, "La ausencia debe distinguirse de la diagonal");
    }

    @Test
    void liberiaYCanasNoTienenAristasHaciaElResto() {
        Grafo grafo = cargarReal();
        int liberia = grafo.indiceDe("Liberia");
        int canas = grafo.indiceDe("Cañas");
        assertTrue(liberia >= 0);
        assertTrue(canas >= 0);
        int cantidad = grafo.lugares().length;
        for (int i = 0; i < cantidad; i++) {
            if (i != liberia && i != canas) {
                assertEquals(Grafo.SIN_CONEXION, grafo.peso(liberia, i));
                assertEquals(Grafo.SIN_CONEXION, grafo.peso(i, liberia));
                assertEquals(Grafo.SIN_CONEXION, grafo.peso(canas, i));
                assertEquals(Grafo.SIN_CONEXION, grafo.peso(i, canas));
            }
        }
    }

    @Test
    void indiceDeEncuentraUbicacionesExistentes() {
        Grafo grafo = cargarReal();
        assertEquals(0, grafo.indiceDe("Liberia"));
        assertEquals(4, grafo.indiceDe("Alajuela"));
    }

    @Test
    void indiceDeDevuelveMenosUnoSiLaUbicacionNoExiste() {
        assertEquals(-1, cargarReal().indiceDe("Ubicación inexistente"));
    }

    @Test
    void indiceDeDevuelveMenosUnoParaNullVacioOBlancos() {
        Grafo grafo = cargarReal();
        assertEquals(-1, grafo.indiceDe(null));
        assertEquals(-1, grafo.indiceDe(""));
        assertEquals(-1, grafo.indiceDe("   "));
    }

    @Test
    void nombreDeDevuelveLaUbicacionDeCadaIndiceValido() {
        Grafo grafo = cargarReal();
        for (int i = 0; i < LUGARES_ESPERADOS.length; i++) {
            assertEquals(LUGARES_ESPERADOS[i], grafo.nombreDe(i));
        }
    }

    @Test
    void nombreDeRechazaIndicesFueraDeRango() {
        Grafo grafo = cargarReal();
        int cantidad = grafo.lugares().length;
        assertThrows(IndexOutOfBoundsException.class, () -> grafo.nombreDe(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> grafo.nombreDe(cantidad));
    }

    @Test
    void pesoRechazaIndicesFueraDeRangoEnAmbosArgumentos() {
        Grafo grafo = cargarReal();
        int cantidad = grafo.lugares().length;
        assertThrows(IndexOutOfBoundsException.class, () -> grafo.peso(-1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> grafo.peso(0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> grafo.peso(cantidad, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> grafo.peso(0, cantidad));
    }

    @Test
    void lugaresDevuelveTodasLasUbicacionesEnOrdenDePrimeraAparicion() {
        assertArrayEquals(LUGARES_ESPERADOS, cargarReal().lugares());
    }

    @Test
    void modificarLugaresNoCambiaElEstadoInternoDelGrafo() {
        Grafo grafo = cargarReal();
        String[] copia = grafo.lugares();
        copia[0] = "Nombre modificado";
        assertArrayEquals(LUGARES_ESPERADOS, grafo.lugares());
        assertEquals("Liberia", grafo.nombreDe(0));
        assertEquals(0, grafo.indiceDe("Liberia"));
        assertEquals(-1, grafo.indiceDe("Nombre modificado"));
    }

    @Test
    void duplicadasConservanElMenorTiempoInclusoEnSentidoContrario() throws IOException {
        Grafo grafo = cargarTemporal("""
                [
                  {"origen": "A", "destino": "B", "tiempo": 50},
                  {"origen": "B", "destino": "A", "tiempo": 20},
                  {"origen": "A", "destino": "B", "tiempo": 35}
                ]
                """);
        assertEquals(2, grafo.lugares().length);
        comprobarConexion(grafo, "A", "B", 20);
    }

    @Test
    void duplicadasDanElMismoResultadoEnLosSeisOrdenesPosibles() throws IOException {
        String[] rutas = {
                "{\"origen\":\"A\",\"destino\":\"B\",\"tiempo\":50}",
                "{\"origen\":\"B\",\"destino\":\"A\",\"tiempo\":20}",
                "{\"origen\":\"A\",\"destino\":\"B\",\"tiempo\":35}"
        };
        int[][] ordenes = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
        for (int[] orden : ordenes) {
            String json = "[" + rutas[orden[0]] + "," + rutas[orden[1]] + "," + rutas[orden[2]] + "]";
            Grafo grafo = cargarTemporal(json);
            assertEquals(2, grafo.lugares().length);
            // Comparamos por nombres: los índices pueden cambiar con el orden del JSON.
            comprobarConexion(grafo, "A", "B", 20);
        }
    }

    @Test
    void archivoInexistenteLanzaExcepcionConSuNombre() {
        Path archivo = temporales.resolve("no-existe.json");
        assertFalse(Files.exists(archivo));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> Grafo.cargar(archivo.toString()));
        assertTrue(error.getMessage().contains("no-existe.json"));
    }

    @Test
    void jsonMalFormadoLanzaExcepcion() {
        assertThrows(IllegalStateException.class, () -> cargarTemporal("[ {\"origen\": "));
    }

    @Test
    void archivoVacioLanzaExcepcion() {
        assertThrows(IllegalStateException.class, () -> cargarTemporal(""));
    }

    @Test
    void archivoConSoloBlancosLanzaExcepcion() {
        assertThrows(IllegalStateException.class, () -> cargarTemporal("  \n\t "));
    }

    @Test
    void jsonNullLanzaExcepcion() {
        assertThrows(IllegalStateException.class, () -> cargarTemporal("null"));
    }

    @Test
    void arregloSinRutasLanzaExcepcion() {
        assertThrows(IllegalStateException.class, () -> cargarTemporal("[]"));
    }

    @Test
    void entradaDeRutaNulaLanzaExcepcion() {
        assertThrows(IllegalStateException.class, () -> cargarTemporal("[null]"));
    }

    @Test
    void origenNuloLanzaExcepcion() {
        assertThrows(IllegalStateException.class, () -> cargarTemporal("""
                [{"origen": null, "destino": "B", "tiempo": 10}]
                """));
    }

    @Test
    void destinoNuloLanzaExcepcion() {
        assertThrows(IllegalStateException.class, () -> cargarTemporal("""
                [{"origen": "A", "destino": null, "tiempo": 10}]
                """));
    }

    @Test
    void origenVacioLanzaExcepcion() {
        assertThrows(IllegalStateException.class, () -> cargarTemporal("""
                [{"origen": "", "destino": "B", "tiempo": 10}]
                """));
    }

    @Test
    void destinoVacioLanzaExcepcion() {
        assertThrows(IllegalStateException.class, () -> cargarTemporal("""
                [{"origen": "A", "destino": "", "tiempo": 10}]
                """));
    }

    @Test
    void origenConSoloBlancosLanzaExcepcion() {
        assertThrows(IllegalStateException.class, () -> cargarTemporal("""
                [{"origen": "   ", "destino": "B", "tiempo": 10}]
                """));
    }

    @Test
    void destinoConSoloBlancosLanzaExcepcion() {
        assertThrows(IllegalStateException.class, () -> cargarTemporal("""
                [{"origen": "A", "destino": "   ", "tiempo": 10}]
                """));
    }

    @Test
    void tiempoNegativoLanzaExcepcion() {
        assertThrows(IllegalStateException.class, () -> cargarTemporal("""
                [{"origen": "A", "destino": "B", "tiempo": -10}]
                """));
    }

    @Test
    void tiempoCeroLanzaExcepcion() {
        assertThrows(IllegalStateException.class, () -> cargarTemporal("""
                [{"origen": "A", "destino": "B", "tiempo": 0}]
                """));
    }

    @Test
    void tiempoAusenteLanzaExcepcion() {
        assertThrows(IllegalStateException.class, () -> cargarTemporal("""
                [{"origen": "A", "destino": "B"}]
                """));
    }

    @Test
    void tiempoFraccionarioLanzaExcepcion() {
        assertThrows(IllegalStateException.class, () -> cargarTemporal("""
                [{"origen": "A", "destino": "B", "tiempo": 1.5}]
                """));
    }

    @Test
    void tiempoFueraDelRangoIntLanzaExcepcion() {
        assertThrows(IllegalStateException.class, () -> cargarTemporal("""
                [{"origen": "A", "destino": "B", "tiempo": 2147483648}]
                """));
    }

    @Test
    void conexionDeUnaUbicacionConsigoMismaLanzaExcepcion() {
        assertThrows(IllegalStateException.class, () -> cargarTemporal("""
                [{"origen": "A", "destino": "A", "tiempo": 10}]
                """));
    }

    @Test
    void rutaDeArchivoNulaOEnBlancoLanzaExcepcionDeArgumento() {
        assertThrows(IllegalArgumentException.class, () -> Grafo.cargar(null));
        assertThrows(IllegalArgumentException.class, () -> Grafo.cargar(""));
        assertThrows(IllegalArgumentException.class, () -> Grafo.cargar("   "));
    }

    @Test
    void cargaDinamicamenteOtrasUbicacionesCantidadesYPesos() throws IOException {
        Grafo grafo = cargarTemporal("""
                [
                  {"origen": "Puerto Azul", "destino": "Valle Verde", "tiempo": 7},
                  {"origen": "Valle Verde", "destino": "Monte Claro", "tiempo": 12},
                  {"origen": "Monte Claro", "destino": "Río Nuevo", "tiempo": 4}
                ]
                """);
        assertArrayEquals(new String[]{"Puerto Azul", "Valle Verde", "Monte Claro", "Río Nuevo"},
                grafo.lugares());
        comprobarConexion(grafo, "Puerto Azul", "Valle Verde", 7);
        comprobarConexion(grafo, "Valle Verde", "Monte Claro", 12);
        comprobarConexion(grafo, "Monte Claro", "Río Nuevo", 4);
        assertEquals(Grafo.SIN_CONEXION,
                grafo.peso(grafo.indiceDe("Puerto Azul"), grafo.indiceDe("Monte Claro")));
        assertEquals(-1, grafo.indiceDe("Liberia"));
    }
}
