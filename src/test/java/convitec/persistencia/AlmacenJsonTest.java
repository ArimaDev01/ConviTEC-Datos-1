package convitec.persistencia;

import convitec.modelo.EstadoPedido;
import convitec.modelo.LineaPedido;
import convitec.modelo.NodoCatalogo;
import convitec.modelo.Pedido;
import convitec.modelo.Producto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AlmacenJsonTest {

    @TempDir
    Path tmp;

    private AlmacenJson almacen;

    @BeforeEach
    void crearAlmacen() {
        almacen = new AlmacenJson(tmp.resolve("ALA").toString());
    }

    private Pedido pedido(String id) {
        return new Pedido(id, "ALA", "2026-11-25T10:00:00", "Heredia", false,
                new LineaPedido[]{new LineaPedido("P01", "Agua", 2, 500, "LIM")});
    }

    @Test
    void creaLaCarpetaDeLaSede() {
        assertTrue(Files.isDirectory(tmp.resolve("ALA")));
    }

    @Test
    void guardarYCargarDevuelveLoMismo() {
        Pedido original = pedido("ALA-0001");
        original.setEstado(EstadoPedido.PENDIENTE);
        almacen.guardar("pedidos.json", new Pedido[]{original, pedido("ALA-0002")});

        Pedido[] cargados = almacen.cargar("pedidos.json", Pedido[].class);
        assertEquals(2, cargados.length);
        assertEquals("ALA-0001", cargados[0].getId());
        assertEquals(EstadoPedido.PENDIENTE, cargados[0].getEstado());
        assertEquals("LIM", cargados[0].getLineas()[0].getSedeQueSuministra());
        assertEquals(1000, cargados[0].getTotal());
        assertEquals("ALA-0002", cargados[1].getId());
    }

    @Test
    void guardaElCatalogoConSusExistencias() {
        NodoCatalogo nodo = new NodoCatalogo(new Producto("P01", "Agua", "Botella 1L", "Bebidas", 500), 4);
        nodo.setExistencia(1, 12);
        almacen.guardar("inventario.json", new NodoCatalogo[]{nodo});

        NodoCatalogo[] cargados = almacen.cargar("inventario.json", NodoCatalogo[].class);
        assertEquals(1, cargados.length);
        assertEquals("P01", cargados[0].getCodigo());
        assertEquals(12, cargados[0].getExistencia(1));
        assertEquals(4, cargados[0].getCantidadSedes());
    }

    @Test
    void guardarDeNuevoReemplazaElContenido() throws IOException {
        almacen.guardar("pedidos.json", new Pedido[]{pedido("ALA-0001"), pedido("ALA-0002")});
        almacen.guardar("pedidos.json", new Pedido[]{pedido("ALA-0003")});
        Pedido[] cargados = almacen.cargar("pedidos.json", Pedido[].class);
        assertEquals(1, cargados.length);
        assertEquals("ALA-0003", cargados[0].getId());
        try (var archivos = Files.list(tmp.resolve("ALA"))) {
            assertEquals(0, archivos.filter(p -> p.toString().endsWith(".tmp")).count(), "no quedan temporales");
        }
    }

    @Test
    void guardarArregloVacio() {
        almacen.guardar("ventas.json", new Pedido[0]);
        assertTrue(almacen.existe("ventas.json"));
        assertEquals(0, almacen.cargar("ventas.json", Pedido[].class).length);
    }

    @Test
    void archivoInexistenteDevuelveArregloVacio() {
        assertFalse(almacen.existe("pedidos.json"));
        Pedido[] cargados = almacen.cargar("pedidos.json", Pedido[].class);
        assertNotNull(cargados);
        assertEquals(0, cargados.length);
    }

    @Test
    void archivoDanadoDevuelveArregloVacioYSeAparta() throws IOException {
        Path carpeta = tmp.resolve("ALA");
        Files.writeString(carpeta.resolve("pedidos.json"), "{ esto no es json [[[");
        assertEquals(0, almacen.cargar("pedidos.json", Pedido[].class).length);

        // El contenido dañado queda en pedidos.json.danado y el siguiente guardar no lo pisa.
        assertEquals("{ esto no es json [[[", Files.readString(carpeta.resolve("pedidos.json.danado")));
        almacen.guardar("pedidos.json", new Pedido[]{pedido("ALA-0001")});
        assertEquals("{ esto no es json [[[", Files.readString(carpeta.resolve("pedidos.json.danado")));
    }

    @Test
    void archivoQueNoEstaEnUtf8SeApartaYNoSePierde() throws IOException {
        // Como si alguien lo editara en el Bloc de notas y lo guardara en ANSI, con una tilde.
        Path carpeta = tmp.resolve("ALA");
        byte[] ansi = "[{\"codigo\":\"P01\",\"nombre\":\"Limón\",\"precio\":5}]".getBytes("ISO-8859-1");
        Files.write(carpeta.resolve("productos.json"), ansi);

        assertEquals(0, almacen.cargar("productos.json", Producto[].class).length);
        almacen.guardar("productos.json", new Producto[0]);
        assertArrayEquals(ansi, Files.readAllBytes(carpeta.resolve("productos.json.danado")));
    }

    @Test
    void siFallaElGuardadoNoQuedaElTemporal() throws IOException {
        // Una carpeta con contenido en el lugar del archivo hace que el renombre falle.
        Path carpeta = tmp.resolve("ALA");
        Files.createDirectories(carpeta.resolve("pedidos.json"));
        Files.writeString(carpeta.resolve("pedidos.json").resolve("algo.txt"), "x");

        assertThrows(java.io.UncheckedIOException.class,
                () -> almacen.guardar("pedidos.json", new Pedido[]{pedido("ALA-0001")}));
        try (var archivos = Files.list(carpeta)) {
            assertEquals(0, archivos.filter(p -> p.toString().endsWith(".tmp")).count(), "no quedan temporales");
        }
    }

    @Test
    void guardadosAlMismoTiempoNoFallanNiDananElArchivo() throws Exception {
        Throwable[] primerError = new Throwable[1];
        Thread[] hilos = new Thread[4];
        for (int h = 0; h < hilos.length; h++) {
            hilos[h] = new Thread(() -> {
                try {
                    for (int i = 0; i < 50; i++) {
                        almacen.guardar("pedidos.json", new Pedido[]{pedido("ALA-0001"), pedido("ALA-0002")});
                    }
                } catch (Throwable e) {
                    primerError[0] = e; // una excepción dentro de un hilo no llega sola a JUnit
                }
            });
            hilos[h].start();
        }
        for (Thread hilo : hilos) {
            hilo.join();
        }
        assertNull(primerError[0], () -> "un guardado falló: " + primerError[0]);
        assertEquals(2, almacen.cargar("pedidos.json", Pedido[].class).length);
        assertFalse(almacen.existe("pedidos.json.danado"));
    }

    @Test
    void archivoVacioDevuelveArregloVacio() throws IOException {
        Files.writeString(tmp.resolve("ALA").resolve("pedidos.json"), "");
        assertEquals(0, almacen.cargar("pedidos.json", Pedido[].class).length);
    }

    @Test
    void rechazaDatosNullYCarpetaVacia() {
        assertThrows(IllegalArgumentException.class, () -> almacen.guardar("pedidos.json", null));
        assertThrows(IllegalArgumentException.class, () -> new AlmacenJson(" "));
    }
}
