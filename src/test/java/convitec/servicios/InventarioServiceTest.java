package convitec.servicios;

import convitec.estructuras.ListaEnlazada;
import convitec.mensajeria.CanalMensajes;
import convitec.mensajeria.ManejadorMensajes;
import convitec.modelo.Mensaje;
import convitec.modelo.TipoMensaje;
import convitec.persistencia.AlmacenJson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static convitec.servicios.Datos.ALA;
import static convitec.servicios.Datos.LIB;
import static convitec.servicios.Datos.SJO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Inventario de ALA con un canal falso que solo anota lo enviado. */
class InventarioServiceTest {

    /** Canal que guarda los mensajes enviados; los recibidos se inyectan con procesar(). */
    static class CanalFalso implements CanalMensajes {
        final ListaEnlazada<Mensaje> enviados = new ListaEnlazada<>();

        @Override
        public synchronized void enviar(Mensaje mensaje) {
            enviados.agregar(mensaje);
        }

        @Override
        public void alRecibir(ManejadorMensajes manejador) {
        }

        synchronized Mensaje[] deTipo(TipoMensaje tipo) {
            ListaEnlazada<Mensaje> encontrados = new ListaEnlazada<>();
            for (Mensaje m : enviados.toArray(new Mensaje[0])) {
                if (m.getTipo() == tipo) {
                    encontrados.agregar(m);
                }
            }
            return encontrados.toArray(new Mensaje[0]);
        }

        synchronized Mensaje ultimo() {
            return enviados.obtener(enviados.tamano() - 1);
        }
    }

    private CanalFalso canal;
    private InventarioService inventario;

    @BeforeEach
    void preparar() {
        canal = new CanalFalso();
        inventario = new InventarioService(Datos.sedes(), ALA, Datos.productos(), canal, null);
    }

    private static Mensaje mensaje(TipoMensaje tipo, String de, String para, String pedido, String codigo, Integer cantidad) {
        Mensaje m = new Mensaje(tipo, de, para);
        m.setPedidoId(pedido);
        m.setCodigo(codigo);
        m.setCantidad(cantidad);
        return m;
    }

    // ---------- agregarUnidades ----------

    @Test
    void agregarUnidadesSumaYAvisaConStock() {
        inventario.agregarUnidades("P01", 5);
        assertEquals(20, inventario.existencia("P01", ALA));
        Mensaje stock = canal.ultimo();
        assertEquals(TipoMensaje.STOCK, stock.getTipo());
        assertEquals("ALA", stock.getDe());
        assertEquals(Mensaje.TODOS, stock.getPara());
        assertEquals("P01", stock.getCodigo());
        assertEquals(20, stock.getCantidad());
    }

    @Test
    void agregarUnidadesRechazaCantidadNoPositiva() {
        assertThrows(IllegalArgumentException.class, () -> inventario.agregarUnidades("P01", 0));
        assertThrows(IllegalArgumentException.class, () -> inventario.agregarUnidades("P01", -3));
        assertEquals(15, inventario.existencia("P01", ALA));
        assertEquals(0, canal.enviados.tamano());
    }

    @Test
    void agregarUnidadesRechazaProductoInexistente() {
        assertThrows(IllegalArgumentException.class, () -> inventario.agregarUnidades("X99", 4));
    }

    @Test
    void productosSalenOrdenadosPorCodigo() {
        var productos = inventario.productos();
        assertEquals(3, productos.length);
        assertEquals("P01", productos[0].getCodigo());
        assertEquals("P03", productos[2].getCodigo());
    }

    // ---------- STOCK y HOLA ----------

    @Test
    void stockRecibidoActualizaLaCopiaDeEsaSede() {
        inventario.procesar(mensaje(TipoMensaje.STOCK, "SJO", Mensaje.TODOS, null, "P01", 7));
        assertEquals(7, inventario.existencia("P01", SJO));
        assertEquals(15, inventario.existencia("P01", ALA));
    }

    @Test
    void stockQueDiceSerDeEstaSedeOMalFormadoSeIgnora() {
        inventario.procesar(mensaje(TipoMensaje.STOCK, "ALA", Mensaje.TODOS, null, "P01", 0));
        inventario.procesar(mensaje(TipoMensaje.STOCK, "XYZ", Mensaje.TODOS, null, "P01", 0));
        inventario.procesar(mensaje(TipoMensaje.STOCK, "SJO", Mensaje.TODOS, null, "X99", 3));
        inventario.procesar(mensaje(TipoMensaje.STOCK, "SJO", Mensaje.TODOS, null, "P01", -2));
        inventario.procesar(mensaje(TipoMensaje.STOCK, "SJO", "LIM", null, "P01", 1));
        assertEquals(15, inventario.existencia("P01", ALA));
        assertEquals(30, inventario.existencia("P01", SJO));
    }

    @Test
    void holaSeRespondeConUnStockPorProducto() {
        inventario.procesar(new Mensaje(TipoMensaje.HOLA, "LIB", Mensaje.TODOS));
        Mensaje[] stocks = canal.deTipo(TipoMensaje.STOCK);
        assertEquals(3, stocks.length);
        for (Mensaje m : stocks) {
            assertEquals("LIB", m.getPara());
            assertEquals(inventario.existencia(m.getCodigo(), ALA), m.getCantidad());
        }
    }

    @Test
    void iniciarSaludaYAnunciaSuExistencia() {
        inventario.iniciar();
        assertEquals(1, canal.deTipo(TipoMensaje.HOLA).length);
        assertEquals(3, canal.deTipo(TipoMensaje.STOCK).length);
    }

    // ---------- Reservas ----------

    @Test
    void reservarConExistenciaRebajaYRespondeQueSi() {
        inventario.procesar(mensaje(TipoMensaje.RESERVAR, "SJO", "ALA", "SJO-1001", "P02", 5));
        assertEquals(7, inventario.existencia("P02", ALA));
        Mensaje respuesta = canal.deTipo(TipoMensaje.RESPUESTA)[0];
        assertEquals("SJO", respuesta.getPara());
        assertEquals("SJO-1001", respuesta.getPedidoId());
        assertEquals("P02", respuesta.getCodigo());
        assertTrue(respuesta.getOk());
        assertNull(respuesta.getMotivo());
        assertEquals(1, inventario.reservas().length);
        // Las demás sedes se enteran de la nueva existencia.
        assertEquals(7, canal.deTipo(TipoMensaje.STOCK)[0].getCantidad());
    }

    @Test
    void reservarRepetidoNoRebajaDosVeces() {
        Mensaje reservar = mensaje(TipoMensaje.RESERVAR, "SJO", "ALA", "SJO-1001", "P02", 5);
        inventario.procesar(reservar);
        inventario.procesar(reservar);
        assertEquals(7, inventario.existencia("P02", ALA));
        assertEquals(1, inventario.reservas().length);
        assertEquals(2, canal.deTipo(TipoMensaje.RESPUESTA).length);
        assertTrue(canal.deTipo(TipoMensaje.RESPUESTA)[1].getOk());
    }

    @Test
    void reservarSinExistenciaSuficienteSeRechazaConMotivo() {
        inventario.procesar(mensaje(TipoMensaje.RESERVAR, "SJO", "ALA", "SJO-1001", "P02", 13));
        assertEquals(12, inventario.existencia("P02", ALA));
        Mensaje respuesta = canal.deTipo(TipoMensaje.RESPUESTA)[0];
        assertFalse(respuesta.getOk());
        assertNotNull(respuesta.getMotivo());
        assertTrue(respuesta.getMotivo().contains("12"));
        assertEquals(0, inventario.reservas().length);
    }

    @Test
    void reservarParaOtraSedeNoSeAtiende() {
        inventario.procesar(mensaje(TipoMensaje.RESERVAR, "SJO", "LIM", "SJO-1001", "P02", 1));
        assertEquals(12, inventario.existencia("P02", ALA));
        assertEquals(0, canal.enviados.tamano());
    }

    @Test
    void confirmarRepetidoNoHaceNadaLaSegundaVez() {
        inventario.procesar(mensaje(TipoMensaje.RESERVAR, "SJO", "ALA", "SJO-1001", "P02", 5));
        Mensaje confirmar = mensaje(TipoMensaje.CONFIRMAR, "SJO", "ALA", "SJO-1001", null, null);
        inventario.procesar(confirmar);
        inventario.procesar(confirmar);
        assertEquals(7, inventario.existencia("P02", ALA));
        assertEquals(0, inventario.reservas().length);
        // Un LIBERAR que llegue después de confirmar tampoco devuelve nada.
        inventario.procesar(mensaje(TipoMensaje.LIBERAR, "SJO", "ALA", "SJO-1001", null, null));
        assertEquals(7, inventario.existencia("P02", ALA));
    }

    @Test
    void liberarDevuelveUnaSolaVez() {
        inventario.procesar(mensaje(TipoMensaje.RESERVAR, "SJO", "ALA", "SJO-1001", "P02", 5));
        Mensaje liberar = mensaje(TipoMensaje.LIBERAR, "SJO", "ALA", "SJO-1001", null, null);
        inventario.procesar(liberar);
        inventario.procesar(liberar);
        assertEquals(12, inventario.existencia("P02", ALA));
        assertEquals(0, inventario.reservas().length);
    }

    @Test
    void reservarDespuesDeLiberarSeRechaza() {
        // Pasa si la sede estaba apagada: al arrancar procesa el RESERVAR viejo después de que el pedido se canceló.
        inventario.procesar(mensaje(TipoMensaje.LIBERAR, "SJO", "ALA", "SJO-1001", null, null));
        inventario.procesar(mensaje(TipoMensaje.RESERVAR, "SJO", "ALA", "SJO-1001", "P02", 5));
        assertEquals(12, inventario.existencia("P02", ALA));
        assertFalse(canal.deTipo(TipoMensaje.RESPUESTA)[0].getOk());
    }

    @Test
    void reservaLocalDirecta() {
        assertNull(inventario.reservar("ALA-1001", "ALA", "P01", 15));
        assertEquals(0, inventario.existencia("P01", ALA));
        assertNotNull(inventario.reservar("ALA-1002", "ALA", "P01", 1));
        inventario.liberar("ALA-1001");
        assertEquals(15, inventario.existencia("P01", ALA));
    }

    // ---------- Persistencia ----------

    @Test
    void alReiniciarSeRecuperaExistenciaYReservas(@TempDir Path carpeta) {
        AlmacenJson almacen = new AlmacenJson(carpeta.toString());
        InventarioService primero = new InventarioService(Datos.sedes(), ALA, Datos.productos(), canal, almacen);
        primero.agregarUnidades("P01", 10);
        primero.procesar(mensaje(TipoMensaje.STOCK, "LIB", Mensaje.TODOS, null, "P03", 2));
        primero.procesar(mensaje(TipoMensaje.RESERVAR, "SJO", "ALA", "SJO-1001", "P02", 4));
        primero.procesar(mensaje(TipoMensaje.LIBERAR, "LIM", "ALA", "LIM-1001", null, null));

        InventarioService segundo = new InventarioService(Datos.sedes(), ALA, Datos.productos(), canal,
                new AlmacenJson(carpeta.toString()));
        assertEquals(25, segundo.existencia("P01", ALA));
        assertEquals(2, segundo.existencia("P03", LIB));
        assertEquals(8, segundo.existencia("P02", ALA));
        assertEquals(1, segundo.reservas().length);
        // La reserva abierta se puede liberar después de reiniciar.
        segundo.procesar(mensaje(TipoMensaje.LIBERAR, "SJO", "ALA", "SJO-1001", null, null));
        assertEquals(12, segundo.existencia("P02", ALA));
        // El pedido cerrado antes de reiniciar sigue cerrado.
        assertNotNull(segundo.reservar("LIM-1001", "LIM", "P01", 1));
    }
}
