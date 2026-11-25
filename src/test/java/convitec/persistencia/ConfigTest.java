package convitec.persistencia;

import convitec.modelo.NodoCatalogo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    @TempDir
    Path config;

    private static final String SEDES = """
            [
              {"codigo": "LIB", "nombre": "Liberia",  "ubicacion": "Liberia",  "puerto": 7001},
              {"codigo": "ALA", "nombre": "Alajuela", "ubicacion": "Alajuela", "puerto": 7002},
              {"codigo": "SJO", "nombre": "San José", "ubicacion": "San José", "puerto": 7003},
              {"codigo": "LIM", "nombre": "Limón",    "ubicacion": "Limón",    "puerto": 7004}
            ]""";

    private static final String PRODUCTOS = """
            [
              {"codigo": "P01", "nombre": "Agua", "descripcion": "Botella 1 L", "categoria": "Bebidas",
               "precio": 500, "existencias": [10, 0, 25, 5]},
              {"codigo": "P02", "nombre": "Pan", "descripcion": "Pan cuadrado", "categoria": "Panadería",
               "precio": 1200}
            ]""";

    private static final String APP = """
            {"rabbitHost": "192.168.0.10", "rabbitPuerto": 5672, "rabbitUsuario": "convitec",
             "rabbitClave": "clave", "carpetaDatos": "data"}""";

    private void escribir(String archivo, String contenido) throws IOException {
        Files.writeString(config.resolve(archivo), contenido);
    }

    @BeforeEach
    void escribirConfigValida() throws IOException {
        escribir("sedes.json", SEDES);
        escribir("productos.json", PRODUCTOS);
        escribir("app.json", APP);
    }

    private Config cargar(String sede) {
        return Config.cargar(config.toString(), sede);
    }

    @Test
    void cargaSedesEnOrdenYLaSedeActual() {
        Config c = cargar("ALA");
        assertEquals(4, c.getCantidadSedes());
        assertEquals("LIB", c.getSedes()[0].getCodigo());
        assertEquals("ALA", c.getSedeActual().getCodigo());
        assertEquals(1, c.getIndiceSedeActual());
        assertEquals(7002, c.getSedeActual().getPuerto());
        assertEquals(3, c.indiceDe("LIM"));
        assertEquals(-1, c.indiceDe("XXX"));
    }

    @Test
    void cargaProductosConExistenciasIniciales() {
        NodoCatalogo[] productos = cargar("ALA").getProductosIniciales();
        assertEquals(2, productos.length);
        assertEquals("Agua", productos[0].getProducto().getNombre());
        assertEquals(25, productos[0].getExistencia(2));
        assertEquals(40, productos[0].getExistenciaTotal());
        assertEquals(0, productos[1].getExistenciaTotal(), "sin existencias en el JSON empieza en 0");
    }

    @Test
    void modificarLosProductosInicialesNoCambiaLaConfig() {
        Config c = cargar("ALA");
        c.getProductosIniciales()[0].setExistencia(0, 999);
        assertEquals(10, c.getProductosIniciales()[0].getExistencia(0));
    }

    @Test
    void cargaAppYRutas() {
        Config c = cargar("LIM");
        assertEquals("192.168.0.10", c.getRabbitHost());
        assertEquals(5672, c.getRabbitPuerto());
        assertEquals("convitec", c.getRabbitUsuario());
        assertEquals(Path.of("data", "LIM").toString(), c.getCarpetaDatosSede());
        assertEquals(config.resolve("rutas.json").toString(), c.getRutaRutas());
    }

    @Test
    void sedeInexistenteDaUnMensajeClaro() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> cargar("XXX"));
        assertTrue(e.getMessage().contains("LIB, ALA, SJO, LIM"));
    }

    @Test
    void faltaUnArchivo() throws IOException {
        Files.delete(config.resolve("app.json"));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> cargar("ALA"));
        assertTrue(e.getMessage().contains("app.json"));
    }

    @Test
    void jsonInvalido() throws IOException {
        escribir("sedes.json", "[ {\"codigo\": ");
        assertThrows(IllegalStateException.class, () -> cargar("ALA"));
    }

    @Test
    void sedeRepetida() throws IOException {
        escribir("sedes.json", """
                [ {"codigo": "ALA", "nombre": "A", "ubicacion": "Alajuela", "puerto": 1},
                  {"codigo": "ALA", "nombre": "B", "ubicacion": "Alajuela", "puerto": 2} ]""");
        assertThrows(IllegalStateException.class, () -> cargar("ALA"));
    }

    @Test
    void productoRepetido() throws IOException {
        escribir("productos.json", """
                [ {"codigo": "P01", "nombre": "Agua", "precio": 500},
                  {"codigo": "P01", "nombre": "Otra", "precio": 600} ]""");
        assertThrows(IllegalStateException.class, () -> cargar("ALA"));
    }

    @Test
    void existenciasConCantidadDistintaALasSedes() throws IOException {
        escribir("productos.json", """
                [ {"codigo": "P01", "nombre": "Agua", "precio": 500, "existencias": [1, 2]} ]""");
        assertThrows(IllegalStateException.class, () -> cargar("ALA"));
    }

    @Test
    void existenciaOPrecioNegativo() throws IOException {
        escribir("productos.json", """
                [ {"codigo": "P01", "nombre": "Agua", "precio": 500, "existencias": [1, -2, 3, 4]} ]""");
        assertThrows(IllegalStateException.class, () -> cargar("ALA"));
        escribir("productos.json", """
                [ {"codigo": "P01", "nombre": "Agua", "precio": -1} ]""");
        assertThrows(IllegalStateException.class, () -> cargar("ALA"));
    }
}
