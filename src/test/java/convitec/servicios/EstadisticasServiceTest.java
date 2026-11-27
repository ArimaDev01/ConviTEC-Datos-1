package convitec.servicios;

import convitec.modelo.LineaPedido;
import convitec.modelo.Pedido;
import convitec.modelo.Producto;
import convitec.modelo.Venta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EstadisticasServiceTest {

    // ---------- Ayudantes ----------

    private static LineaPedido linea(String codigo, int cantidad, double precio) {
        return new LineaPedido(codigo, "Producto " + codigo, cantidad, precio, "ALA");
    }

    /** Venta entregada a domicilio con el tiempo dado (0 = todavía no entregada). */
    private static Venta venta(String id, int tiempo, LineaPedido... lineas) {
        Pedido pedido = new Pedido(id, "ALA", "2026-11-26T10:00:00", "Heredia", false, lineas);
        Venta venta = new Venta(pedido, "2026-11-26T10:00:00");
        venta.setTiempoEntrega(tiempo);
        return venta;
    }

    private static Venta mostrador(String id, LineaPedido... lineas) {
        Pedido pedido = new Pedido(id, "ALA", "2026-11-26T10:00:00", "Alajuela", true, lineas);
        return new Venta(pedido, "2026-11-26T10:00:00");
    }

    private static EstadisticasService con(Venta... ventas) {
        return new EstadisticasService(() -> ventas);
    }

    private static String[] codigos(Producto[] productos) {
        String[] codigos = new String[productos.length];
        for (int i = 0; i < productos.length; i++) {
            codigos[i] = productos[i].getCodigo();
        }
        return codigos;
    }

    private static String[] ids(Venta[] ventas) {
        String[] ids = new String[ventas.length];
        for (int i = 0; i < ventas.length; i++) {
            ids[i] = ventas[i].getPedidoId();
        }
        return ids;
    }

    // ---------- Sin ventas ----------

    @Test
    void sinVentasTodoSaleVacio() {
        EstadisticasService stats = con();
        assertNull(stats.estrella());
        assertEquals(0, stats.masVendidos().length);
        assertEquals(0, stats.peoresPedidos().length);
        assertEquals(0, stats.ingresosTotales());
    }

    @Test
    void fuenteQueDevuelveNullSeTrataComoVacia() {
        EstadisticasService stats = new EstadisticasService(() -> null);
        assertNull(stats.estrella());
        assertEquals(0, stats.peoresPedidos().length);
    }

    @Test
    void fuenteNullSeRechaza() {
        assertThrows(IllegalArgumentException.class, () -> new EstadisticasService(null));
    }

    // ---------- Producto estrella ----------

    @Test
    void estrellaEsElDeMasIngresosAunqueVendaMenosUnidades() {
        // P01: 10 × 500 = 5000. P02: 2 × 3000 = 6000 → gana P02.
        EstadisticasService stats = con(
                venta("V1", 30, linea("P01", 10, 500)),
                venta("V2", 30, linea("P02", 2, 3000)));
        assertEquals("P02", stats.estrella().getCodigo());
    }

    @Test
    void estrellaSumaElMismoProductoEnVariasVentas() {
        // P01: 3 × 1000 + 4 × 1000 = 7000 contra P02: 1 × 6000.
        EstadisticasService stats = con(
                venta("V1", 30, linea("P01", 3, 1000), linea("P02", 1, 6000)),
                mostrador("V2", linea("P01", 4, 1000)));
        assertEquals("P01", stats.estrella().getCodigo());
        assertEquals(7000, stats.rankingPorIngresos()[0].getIngresos());
        assertEquals(7, stats.rankingPorIngresos()[0].getUnidades());
    }

    @Test
    void empateDeIngresosLoGanaElDeMasUnidades() {
        // Los dos suman 2000, pero P02 vendió 4 unidades.
        EstadisticasService stats = con(venta("V1", 30, linea("P01", 1, 2000), linea("P02", 4, 500)));
        assertEquals("P02", stats.estrella().getCodigo());
    }

    @Test
    void empateTotalSeResuelvePorCodigo() {
        EstadisticasService stats = con(venta("V1", 30, linea("P09", 2, 1000), linea("P03", 2, 1000)));
        assertEquals("P03", stats.estrella().getCodigo());
    }

    // ---------- Más vendidos ----------

    @Test
    void masVendidosOrdenaPorUnidades() {
        EstadisticasService stats = con(
                venta("V1", 30, linea("P01", 1, 9000), linea("P02", 5, 100)),
                venta("V2", 30, linea("P03", 3, 100), linea("P02", 1, 100)));
        assertArrayEquals(new String[]{"P02", "P03", "P01"}, codigos(stats.masVendidos()));
    }

    @Test
    void lineasConCantidadCeroSeIgnoran() {
        EstadisticasService stats = con(venta("V1", 30, linea("P01", 0, 1000), linea("P02", 1, 10)));
        assertArrayEquals(new String[]{"P02"}, codigos(stats.masVendidos()));
    }

    // ---------- Peores pedidos ----------

    @Test
    void peoresPedidosDeMenorAMayorRendimiento() {
        // Rendimientos: V1 = 6000/60 = 100, V2 = 1000/50 = 20, V3 = 9000/30 = 300.
        EstadisticasService stats = con(
                venta("V1", 60, linea("P01", 6, 1000)),
                venta("V2", 50, linea("P01", 1, 1000)),
                venta("V3", 30, linea("P01", 9, 1000)));
        assertArrayEquals(new String[]{"V2", "V1", "V3"}, ids(stats.peoresPedidos()));
    }

    @Test
    void ventaDeMostradorNoSaleEnPeoresPedidos() {
        EstadisticasService stats = con(
                mostrador("M1", linea("P01", 1, 1)),
                venta("V1", 40, linea("P01", 1, 1000)));
        assertArrayEquals(new String[]{"V1"}, ids(stats.peoresPedidos()));
    }

    @Test
    void ventaSinEntregarNoSaleEnPeoresPedidos() {
        EstadisticasService stats = con(venta("V1", 0, linea("P01", 1, 1000)));
        assertEquals(0, stats.peoresPedidos().length);
    }

    @Test
    void empateDeRendimientoConservaElOrdenOriginal() {
        // Los dos rinden 10 ₡/min: merge sort es estable, así que V1 queda antes que V2.
        EstadisticasService stats = con(
                venta("V1", 100, linea("P01", 1, 1000)),
                venta("V2", 50, linea("P01", 1, 500)));
        assertArrayEquals(new String[]{"V1", "V2"}, ids(stats.peoresPedidos()));
    }

    @Test
    void ingresosTotalesSumaTodasLasVentas() {
        EstadisticasService stats = con(
                venta("V1", 30, linea("P01", 2, 1500)),
                mostrador("M1", linea("P02", 1, 700)));
        assertEquals(3700, stats.ingresosTotales());
    }
}
