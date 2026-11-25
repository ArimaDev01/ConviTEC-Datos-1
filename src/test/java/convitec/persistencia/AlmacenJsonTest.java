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
    void guardarDeNuevoReemplazaElContenido() {
        almacen.guardar("pedidos.json", new Pedido[]{pedido("ALA-0001"), pedido("ALA-0002")});
        almacen.guardar("pedidos.json", new Pedido[]{pedido("ALA-0003")});
        Pedido[] cargados = almacen.cargar("pedidos.json", Pedido[].class);
        assertEquals(1, cargados.length);
        assertEquals("ALA-0003", cargados[0].getId());
        assertFalse(Files.exists(tmp.resolve("ALA").resolve("pedidos.json.tmp")));
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
    void archivoDanadoDevuelveArregloVacio() throws IOException {
        Files.writeString(tmp.resolve("ALA").resolve("pedidos.json"), "{ esto no es json [[[");
        assertEquals(0, almacen.cargar("pedidos.json", Pedido[].class).length);
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
