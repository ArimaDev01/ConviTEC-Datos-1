package convitec.mensajeria;

import com.google.gson.Gson;
import convitec.modelo.Mensaje;
import convitec.modelo.TipoMensaje;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Pruebas del filtro de mensajes (no necesitan un servidor de RabbitMQ). */
class ClienteRabbitTest {
    private final Gson gson = new Gson();

    private Mensaje interpretar(String json, String sede) {
        return ClienteRabbit.interpretar(json.getBytes(StandardCharsets.UTF_8), sede, gson);
    }

    @Test
    void mensajeParaEstaSedeSeAcepta() {
        Mensaje m = interpretar("{\"tipo\":\"RESERVAR\",\"de\":\"SJO\",\"para\":\"ALA\",\"pedidoId\":\"SJO-1001\","
                + "\"codigo\":\"P01\",\"cantidad\":3}", "ALA");
        assertNotNull(m);
        assertEquals(TipoMensaje.RESERVAR, m.getTipo());
        assertEquals("SJO-1001", m.getPedidoId());
        assertEquals(3, m.getCantidad());
    }

    @Test
    void mensajeParaTodasSeAcepta() {
        assertNotNull(interpretar("{\"tipo\":\"HOLA\",\"de\":\"LIM\",\"para\":\"TODOS\"}", "ALA"));
    }

    @Test
    void mensajeParaOtraSedeSeIgnora() {
        assertNull(interpretar("{\"tipo\":\"RESERVAR\",\"de\":\"SJO\",\"para\":\"LIM\"}", "ALA"));
    }

    @Test
    void mensajePropioSeIgnora() {
        assertNull(interpretar("{\"tipo\":\"STOCK\",\"de\":\"ALA\",\"para\":\"TODOS\"}", "ALA"));
    }

    @Test
    void jsonRotoSeIgnora() {
        assertNull(interpretar("{\"tipo\":\"STOCK\", de: ", "ALA"));
        assertNull(interpretar("esto no es json", "ALA"));
    }

    @Test
    void tipoDesconocidoSeIgnora() {
        assertNull(interpretar("{\"tipo\":\"SALUDO\",\"de\":\"SJO\",\"para\":\"TODOS\"}", "ALA"));
    }

    @Test
    void mensajeSinRemitenteSeIgnora() {
        assertNull(interpretar("{\"tipo\":\"STOCK\",\"para\":\"TODOS\"}", "ALA"));
        assertNull(ClienteRabbit.interpretar(null, "ALA", gson));
    }

    @Test
    void idaYVueltaEnJsonConservaLosCampos() {
        Mensaje original = new Mensaje(TipoMensaje.RESPUESTA, "LIM", "ALA");
        original.setPedidoId("ALA-1002");
        original.setCodigo("P02");
        original.setCantidad(4);
        original.setOk(false);
        original.setMotivo("sin existencia");
        Mensaje leido = interpretar(gson.toJson(original), "ALA");
        assertEquals(TipoMensaje.RESPUESTA, leido.getTipo());
        assertEquals(false, leido.getOk());
        assertEquals("sin existencia", leido.getMotivo());
    }

    @Test
    void enviarSinConectarFallaConMensajeClaro() {
        ClienteRabbit cliente = new ClienteRabbit("ALA");
        assertThrows(IllegalStateException.class, () -> cliente.enviar(new Mensaje(TipoMensaje.HOLA, "ALA", "TODOS")));
        assertEquals("sede.ALA", ClienteRabbit.nombreCola("ALA"));
    }
}
