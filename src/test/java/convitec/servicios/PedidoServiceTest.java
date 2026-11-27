package convitec.servicios;

import convitec.modelo.EstadoPedido;
import convitec.modelo.LineaPedido;
import convitec.modelo.Pedido;
import convitec.modelo.ResultadoPedido;
import convitec.modelo.Venta;
import convitec.persistencia.AlmacenJson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static convitec.servicios.Datos.ALA;
import static convitec.servicios.Datos.LIB;
import static convitec.servicios.Datos.LIM;
import static convitec.servicios.Datos.SJO;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Las 4 sedes en memoria, conectadas por BusMemoria (como si fuera RabbitMQ).
 * Los pedidos se hacen en ALA. Existencias iniciales:
 * P01 [LIB 20, ALA 15, SJO 30, LIM 10], P02 [5, 12, 0, 8], P03 [10, 0, 25, 4].
 */
class PedidoServiceTest {
    private static final long ESPERA_MS = 400;
    private static final String[] CODIGOS = {"LIB", "ALA", "SJO", "LIM"};

    private BusMemoria bus;
    private BusMemoria.Extremo[] canales;
    private InventarioService[] inventarios;
    private PedidoService[] pedidos;

    @BeforeEach
    void levantarSedes() throws Exception {
        bus = new BusMemoria();
        canales = new BusMemoria.Extremo[4];
        inventarios = new InventarioService[4];
        pedidos = new PedidoService[4];
        for (int i = 0; i < 4; i++) {
            canales[i] = bus.conectar(CODIGOS[i]);
            inventarios[i] = new InventarioService(Datos.sedes(), i, Datos.productos(), canales[i], null);
            pedidos[i] = new PedidoService(inventarios[i], canales[i], new RutasFalsas(), null, ESPERA_MS);
        }
        for (int i = 0; i < 4; i++) {
            inventarios[i].iniciar();
        }
        bus.sincronizar();
    }

    @AfterEach
    void cerrar() {
        bus.cerrar();
    }

    private static LineaPedido[] items(Object... codigoYCantidad) {
        LineaPedido[] lineas = new LineaPedido[codigoYCantidad.length / 2];
        for (int i = 0; i < lineas.length; i++) {
            lineas[i] = new LineaPedido((String) codigoYCantidad[2 * i], (Integer) codigoYCantidad[2 * i + 1]);
        }
        return lineas;
    }

    private int existencia(int enSede, String codigo, int deSede) {
        return inventarios[enSede].existencia(codigo, deSede);
    }

    /** La existencia de un producto vista desde las 4 sedes debe ser la misma. */
    private void assertMismaVistaEnTodas(String codigo) {
        for (int sede = 0; sede < 4; sede++) {
            int real = existencia(sede, codigo, sede);
            for (int observador = 0; observador < 4; observador++) {
                assertEquals(real, existencia(observador, codigo, sede),
                        CODIGOS[observador] + " ve mal la existencia de " + codigo + " en " + CODIGOS[sede]);
            }
        }
    }

    // ---------- Pedidos aceptados ----------

    @Test
    void pedidoSoloConExistenciaLocal() throws Exception {
        ResultadoPedido r = pedidos[ALA].registrar("Heredia", false, items("P01", 4));
        assertTrue(r.isOk(), r.getMensaje());
        Pedido p = r.getPedido();
        assertEquals("ALA-1001", p.getId());
        assertEquals(EstadoPedido.PENDIENTE, p.getEstado());
        assertEquals("Heredia", p.getDestino());
        assertEquals(4 * 650.0, p.getTotal());
        bus.sincronizar();
        assertEquals(11, existencia(ALA, "P01", ALA));
        assertMismaVistaEnTodas("P01");
    }

    @Test
    void pedidoConProductosDeDosSedes() throws Exception {
        // ALA tiene 12 de café: los 3 que faltan salen de LIM (8), la otra sede que más tiene.
        ResultadoPedido r = pedidos[ALA].registrar("Cartago", false, items("P02", 15));
        assertTrue(r.isOk(), r.getMensaje());
        Pedido p = r.getPedido();
        assertEquals(2, p.getLineas().length);
        assertEquals("ALA", p.getLineas()[0].getSedeQueSuministra());
        assertEquals(12, p.getLineas()[0].getCantidad());
        assertEquals("LIM", p.getLineas()[1].getSedeQueSuministra());
        assertEquals(3, p.getLineas()[1].getCantidad());
        assertArrayEquals(new String[]{"ALA", "LIM"}, p.getSedesParticipantes());

        bus.sincronizar();
        assertEquals(0, existencia(ALA, "P02", ALA));
        assertEquals(5, existencia(LIM, "P02", LIM));
        assertEquals(0, inventarios[LIM].reservas().length, "LIM recibió CONFIRMAR y cerró la reserva");
        assertMismaVistaEnTodas("P02");

        // Pendiente, en la cola y con su venta.
        assertEquals(1, pedidos[ALA].pendientes().tamano());
        Venta[] ventas = pedidos[ALA].ventas();
        assertEquals(1, ventas.length);
        assertEquals(15 * 2450.0, ventas[0].getMontoTotal());
        assertArrayEquals(new String[]{"ALA", "LIM"}, ventas[0].getSedesParticipantes());
        assertFalse(ventas[0].isMostrador());
    }

    @Test
    void productoQueNoHayLocalSaleCompletoDeOtraSede() throws Exception {
        ResultadoPedido r = pedidos[ALA].registrar("San José", false, items("P03", 20));
        assertTrue(r.isOk(), r.getMensaje());
        assertEquals(1, r.getPedido().getLineas().length);
        assertEquals("SJO", r.getPedido().getLineas()[0].getSedeQueSuministra());
        bus.sincronizar();
        assertEquals(5, existencia(SJO, "P03", SJO));
        assertMismaVistaEnTodas("P03");
    }

    @Test
    void lineasRepetidasSeSuman() {
        ResultadoPedido r = pedidos[ALA].registrar("Heredia", false, items("P01", 2, "P03", 1, "P01", 3));
        assertTrue(r.isOk(), r.getMensaje());
        LineaPedido[] lineas = r.getPedido().getLineas();
        assertEquals("P01", lineas[0].getCodigo());
        assertEquals(5, lineas[0].getCantidad());
        assertEquals("P03", lineas[1].getCodigo());
        assertEquals(2, lineas.length);
    }

    @Test
    void ventaDeMostradorQuedaEntregadaSinRutaNiCola() throws Exception {
        // Liberia es inalcanzable para RutasFalsas, pero el mostrador no necesita ruta.
        ResultadoPedido r = pedidos[LIB].registrar(null, true, items("P01", 2));
        assertTrue(r.isOk(), r.getMensaje());
        Pedido p = r.getPedido();
        assertEquals(EstadoPedido.ENTREGADO, p.getEstado());
        assertTrue(p.isMostrador());
        assertEquals("Liberia", p.getDestino());
        assertEquals(0, pedidos[LIB].pendientes().tamano());
        Venta venta = pedidos[LIB].ventas()[0];
        assertTrue(venta.isMostrador());
        assertEquals(0, venta.getTiempoEntrega());
        assertFalse(venta.tieneRendimiento());
        bus.sincronizar();
        assertEquals(18, existencia(ALA, "P01", LIB));
    }

    @Test
    void losIdsSonConsecutivosPorSede() {
        assertEquals("ALA-1001", pedidos[ALA].registrar("Heredia", false, items("P01", 1)).getPedido().getId());
        assertEquals("ALA-1002", pedidos[ALA].registrar("Heredia", false, items("P01", 1000)).getPedido().getId());
        assertEquals("ALA-1003", pedidos[ALA].registrar("Heredia", false, items("P01", 1)).getPedido().getId());
        assertEquals("SJO-1001", pedidos[SJO].registrar("Heredia", false, items("P01", 1)).getPedido().getId());
        assertEquals(3, pedidos[ALA].pedidos().length);
    }

    // ---------- Pedidos rechazados ----------

    @Test
    void existenciaInsuficienteSeRechazaSinReservarNada() throws Exception {
        ResultadoPedido r = pedidos[ALA].registrar("Heredia", false, items("P02", 26));
        assertFalse(r.isOk());
        assertTrue(r.getMensaje().contains("25"), r.getMensaje());
        assertEquals(EstadoPedido.CANCELADO, r.getPedido().getEstado());
        assertEquals(r.getMensaje(), r.getPedido().getMotivoCancelacion());
        bus.sincronizar();
        assertEquals(12, existencia(ALA, "P02", ALA));
        assertEquals(8, existencia(LIM, "P02", LIM));
        assertEquals(0, pedidos[ALA].ventas().length);
        assertEquals(1, pedidos[ALA].pedidos().length, "el rechazado queda en el historial");
    }

    @Test
    void productoInexistenteSeRechaza() {
        ResultadoPedido r = pedidos[ALA].registrar("Heredia", false, items("X99", 1));
        assertFalse(r.isOk());
        assertTrue(r.getMensaje().contains("X99"));
    }

    @Test
    void destinoInalcanzableLiberaLasReservas() throws Exception {
        ResultadoPedido r = pedidos[ALA].registrar("Cañas", false, items("P02", 15, "P01", 3));
        assertFalse(r.isOk());
        assertTrue(r.getMensaje().contains("Cañas"), r.getMensaje());
        assertEquals(EstadoPedido.CANCELADO, r.getPedido().getEstado());
        bus.sincronizar();
        assertEquals(12, existencia(ALA, "P02", ALA));
        assertEquals(15, existencia(ALA, "P01", ALA));
        assertEquals(8, existencia(LIM, "P02", LIM), "LIM recibió LIBERAR y devolvió sus 3");
        assertEquals(0, inventarios[LIM].reservas().length);
        assertEquals(0, inventarios[ALA].reservas().length);
        assertMismaVistaEnTodas("P02");
        assertEquals(0, pedidos[ALA].pendientes().tamano());
        assertEquals(0, pedidos[ALA].ventas().length);
    }

    @Test
    void sedeApagadaHaceQueElPedidoExpireYSeLibere() throws Exception {
        canales[LIM].apagado = true;
        long inicio = System.currentTimeMillis();
        ResultadoPedido r = pedidos[ALA].registrar("Cartago", false, items("P02", 15));
        long tardo = System.currentTimeMillis() - inicio;
        assertFalse(r.isOk());
        assertTrue(r.getMensaje().contains("LIM"), r.getMensaje());
        assertTrue(tardo >= ESPERA_MS - 20 && tardo < ESPERA_MS + 2000, "esperó " + tardo + " ms");
        bus.sincronizar();
        assertEquals(12, existencia(ALA, "P02", ALA), "lo reservado en ALA se devolvió");
        assertEquals(0, inventarios[ALA].reservas().length);
        assertEquals(EstadoPedido.CANCELADO, r.getPedido().getEstado());
    }

    @Test
    void rechazoDeUnaSedeCancelaYLiberaLoDeLasDemas() throws Exception {
        // ALA cree que LIM tiene 8 de café, pero LIM vendió todo y su STOCK todavía no llegó.
        canales[ALA].apagado = true;
        assertTrue(pedidos[LIM].registrar(null, true, items("P02", 8)).isOk());
        bus.sincronizar();
        canales[ALA].apagado = false;
        assertEquals(8, existencia(ALA, "P02", LIM), "ALA tiene un dato viejo");

        ResultadoPedido r = pedidos[ALA].registrar("Cartago", false, items("P02", 15));
        assertFalse(r.isOk());
        assertTrue(r.getMensaje().contains("LIM"), r.getMensaje());
        bus.sincronizar();
        assertEquals(12, existencia(ALA, "P02", ALA));
        assertEquals(0, existencia(ALA, "P02", LIM), "la respuesta de LIM vino con su STOCK actualizado");
    }

    @Test
    void entradaInvalidaLanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> pedidos[ALA].registrar("Heredia", false, items()));
        assertThrows(IllegalArgumentException.class, () -> pedidos[ALA].registrar("Heredia", false, null));
        assertThrows(IllegalArgumentException.class, () -> pedidos[ALA].registrar("Heredia", false, items("P01", 0)));
        assertThrows(IllegalArgumentException.class, () -> pedidos[ALA].registrar(" ", false, items("P01", 1)));
        assertEquals(0, pedidos[ALA].pedidos().length);
    }

    @Test
    void pedidosSimultaneosNoVendenMasDeLoQueHay() throws Exception {
        // SJO y LIM piden al mismo tiempo todo el café de ALA (12): solo uno puede ganarlo.
        ResultadoPedido[] resultados = new ResultadoPedido[2];
        Thread a = new Thread(() -> resultados[0] = pedidos[SJO].registrar("Heredia", false, items("P02", 12)));
        Thread b = new Thread(() -> resultados[1] = pedidos[LIB].registrar("Heredia", false, items("P02", 17)));
        a.start();
        b.start();
        a.join();
        b.join();
        bus.sincronizar();
        int vendidos = 0;
        for (ResultadoPedido r : resultados) {
            if (r.isOk()) {
                for (LineaPedido l : r.getPedido().getLineas()) {
                    vendidos += l.getCantidad();
                }
            }
        }
        int queda = 0;
        for (int s = 0; s < 4; s++) {
            queda += existencia(s, "P02", s);
        }
        assertEquals(25, vendidos + queda, "no se crearon ni se perdieron unidades");
        assertMismaVistaEnTodas("P02");
    }

    // ---------- Entregas y persistencia ----------

    @Test
    void marcarEntregadoActualizaPedidoYVenta() {
        Pedido p = pedidos[ALA].registrar("Heredia", false, items("P01", 2)).getPedido();
        assertTrue(pedidos[ALA].marcarEntregado(p.getId(), 20));
        assertEquals(EstadoPedido.ENTREGADO, p.getEstado());
        assertEquals(20, p.getTiempoTraslado());
        assertEquals(20, pedidos[ALA].ventas()[0].getTiempoEntrega());
        assertFalse(pedidos[ALA].marcarEntregado("ALA-9999", 5));
    }

    @Test
    void alReiniciarSeRecuperanPedidosVentasYPendientes(@TempDir Path carpeta) throws Exception {
        BusMemoria.Extremo canal = bus.conectar("ALA");
        canales[ALA].apagado = true; // la "vieja" ALA ya no escucha
        InventarioService inv = new InventarioService(Datos.sedes(), ALA, Datos.productos(), canal,
                new AlmacenJson(carpeta.toString()));
        PedidoService servicio = new PedidoService(inv, canal, new RutasFalsas(),
                new AlmacenJson(carpeta.toString()), ESPERA_MS);
        assertTrue(servicio.registrar("Heredia", false, items("P01", 2)).isOk());
        assertTrue(servicio.registrar(null, true, items("P01", 1)).isOk());
        assertFalse(servicio.registrar("Cañas", false, items("P01", 1)).isOk());
        assertTrue(servicio.registrar("Cartago", false, items("P02", 15)).isOk()); // con LIM
        bus.sincronizar();

        // "Reinicio": servicios nuevos sobre la misma carpeta.
        canal.apagado = true;
        BusMemoria.Extremo canal2 = bus.conectar("ALA");
        InventarioService inv2 = new InventarioService(Datos.sedes(), ALA, Datos.productos(), canal2,
                new AlmacenJson(carpeta.toString()));
        PedidoService servicio2 = new PedidoService(inv2, canal2, new RutasFalsas(),
                new AlmacenJson(carpeta.toString()), ESPERA_MS);
        servicio2.iniciar();

        assertEquals(4, servicio2.pedidos().length);
        assertEquals(3, servicio2.ventas().length);
        Pedido[] pendientes = servicio2.pendientesArreglo();
        assertEquals(2, pendientes.length);
        assertEquals("ALA-1001", pendientes[0].getId());
        assertEquals("ALA-1004", pendientes[1].getId());
        assertEquals(12, inv2.existencia("P01", ALA));
        assertEquals(0, inv2.existencia("P02", ALA));
        assertEquals("ALA-1005", servicio2.registrar(null, true, items("P01", 1)).getPedido().getId());
    }
}
