package convitec.modelo;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModeloTest {

    private Pedido pedidoDeDosSedes() {
        LineaPedido[] lineas = {
                new LineaPedido("P01", "Agua", 2, 500, "ALA"),
                new LineaPedido("P02", "Pan", 1, 1200, "LIM"),
                new LineaPedido("P01", "Agua", 1, 500, "LIM")
        };
        return new Pedido("ALA-0001", "ALA", "2026-11-25T10:00:00", "Heredia", false, lineas);
    }

    @Test
    void nodoCatalogoEmpiezaEnCeroYSumaExistencias() {
        NodoCatalogo nodo = new NodoCatalogo(new Producto("P01", "Agua", "Botella 1L", "Bebidas", 500), 4);
        assertEquals(0, nodo.getExistenciaTotal());
        nodo.setExistencia(0, 5);
        nodo.setExistencia(3, 7);
        assertEquals(5, nodo.getExistencia(0));
        assertEquals(12, nodo.getExistenciaTotal());
    }

    @Test
    void nodoCatalogoRechazaSedeInexistenteYExistenciaNegativa() {
        NodoCatalogo nodo = new NodoCatalogo(new Producto("P01", "Agua", "", "Bebidas", 500), 4);
        assertThrows(IndexOutOfBoundsException.class, () -> nodo.getExistencia(4));
        assertThrows(IndexOutOfBoundsException.class, () -> nodo.setExistencia(-1, 3));
        assertThrows(IllegalArgumentException.class, () -> nodo.setExistencia(0, -1));
    }

    @Test
    void getExistenciasDevuelveUnaCopia() {
        NodoCatalogo nodo = new NodoCatalogo(new Producto("P01", "Agua", "", "Bebidas", 500), 4);
        nodo.getExistencias()[0] = 99;
        assertEquals(0, nodo.getExistencia(0));
    }

    @Test
    void pedidoCalculaTotalYSedesParticipantesSinRepetir() {
        Pedido pedido = pedidoDeDosSedes();
        assertEquals(EstadoPedido.RESERVANDO, pedido.getEstado());
        assertEquals(2 * 500 + 1200 + 500, pedido.getTotal());
        assertArrayEquals(new String[]{"ALA", "LIM"}, pedido.getSedesParticipantes());
    }

    @Test
    void pedidoVacioNoFalla() {
        Pedido pedido = new Pedido("ALA-0002", "ALA", "2026-11-25T10:00:00", "Heredia", false, null);
        assertEquals(0, pedido.getTotal());
        assertEquals(0, pedido.getSedesParticipantes().length);
    }

    @Test
    void cancelarGuardaElMotivo() {
        Pedido pedido = pedidoDeDosSedes();
        pedido.cancelar("Cañas no es alcanzable desde Alajuela");
        assertEquals(EstadoPedido.CANCELADO, pedido.getEstado());
        assertEquals("Cañas no es alcanzable desde Alajuela", pedido.getMotivoCancelacion());
    }

    @Test
    void ventaCopiaDatosDelPedidoYCalculaRendimiento() {
        Venta venta = new Venta(pedidoDeDosSedes(), "2026-11-25T10:01:00");
        assertEquals("ALA", venta.getSede());
        assertArrayEquals(new String[]{"ALA", "LIM"}, venta.getSedesParticipantes());
        assertEquals(2700, venta.getMontoTotal());
        assertFalse(venta.tieneRendimiento());
        venta.setTiempoEntrega(20);
        assertTrue(venta.tieneRendimiento());
        assertEquals(135, venta.getRendimiento());
    }

    @Test
    void ventaDeMostradorNoTieneRendimiento() {
        Pedido pedido = new Pedido("SJO-0001", "SJO", "2026-11-25T10:00:00", null, true,
                new LineaPedido[]{new LineaPedido("P01", "Agua", 1, 500, "SJO")});
        Venta venta = new Venta(pedido, "2026-11-25T10:00:00");
        assertTrue(venta.isMostrador());
        assertEquals(0, venta.getTiempoEntrega());
        assertFalse(venta.tieneRendimiento());
        assertThrows(IllegalStateException.class, venta::getRendimiento);
    }

    @Test
    void mensajeSabeSiEsParaEstaSede() {
        Mensaje paraTodos = new Mensaje(TipoMensaje.STOCK, "ALA", Mensaje.TODOS);
        Mensaje paraLimon = new Mensaje(TipoMensaje.RESERVAR, "ALA", "LIM");
        assertTrue(paraTodos.esPara("SJO"));
        assertTrue(paraLimon.esPara("LIM"));
        assertFalse(paraLimon.esPara("SJO"));
    }

    @Test
    void gsonGuardaYCargaPedidosYMensajes() {
        Gson gson = new Gson();
        Pedido original = pedidoDeDosSedes();
        original.setEstado(EstadoPedido.PENDIENTE);

        Pedido[] cargados = gson.fromJson(gson.toJson(new Pedido[]{original}), Pedido[].class);
        assertEquals(1, cargados.length);
        assertEquals("ALA-0001", cargados[0].getId());
        assertEquals(EstadoPedido.PENDIENTE, cargados[0].getEstado());
        assertEquals(3, cargados[0].getLineas().length);
        assertEquals(original.getTotal(), cargados[0].getTotal());

        Mensaje mensaje = new Mensaje(TipoMensaje.RESERVAR, "ALA", "LIM");
        mensaje.setCodigo("P02");
        mensaje.setCantidad(3);
        Mensaje leido = gson.fromJson(gson.toJson(mensaje), Mensaje.class);
        assertEquals(TipoMensaje.RESERVAR, leido.getTipo());
        assertEquals(3, leido.getCantidad());
        assertNull(leido.getOk());
    }
}
