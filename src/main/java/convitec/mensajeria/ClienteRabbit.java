package convitec.mensajeria;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import convitec.estructuras.ListaEnlazada;
import convitec.modelo.Mensaje;
import convitec.persistencia.Config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * Cliente de RabbitMQ de una sede.
 *
 * <pre>
 *   exchange "convitec" (fanout)  ──►  cola sede.LIB
 *                                 ──►  cola sede.ALA
 *                                 ──►  cola sede.SJO
 *                                 ──►  cola sede.LIM
 * </pre>
 * Todas las sedes publican en el mismo exchange fanout, así cada mensaje llega a la cola de
 * todas. Cada sede lee solo su cola y descarta lo que no le toca:
 * <ul>
 *   <li>mensajes que ella misma envió,</li>
 *   <li>mensajes con "para" de otra sede,</li>
 *   <li>JSON roto o de un tipo que no existe (Gson deja el tipo en null).</li>
 * </ul>
 * La cola es durable: si la sede está apagada, los mensajes la esperan y se procesan
 * en orden al arrancar (por ejemplo un RESERVAR seguido de su LIBERAR).
 */
public class ClienteRabbit implements CanalMensajes {
    public static final String EXCHANGE = "convitec";

    private final String codigoSede;
    private final ListaEnlazada<ManejadorMensajes> manejadores = new ListaEnlazada<>();
    private final Gson gson = new Gson();
    private Connection conexion;
    private Channel canal;

    public ClienteRabbit(String codigoSede) {
        if (codigoSede == null || codigoSede.isBlank()) {
            throw new IllegalArgumentException("Hay que indicar el código de la sede");
        }
        this.codigoSede = codigoSede;
    }

    /** Nombre de la cola de una sede, por ejemplo sede.ALA. */
    public static String nombreCola(String codigoSede) {
        return "sede." + codigoSede;
    }

    /** Se conecta con los datos de app.json, declara el exchange y la cola y empieza a escuchar. */
    public void conectar(Config config) {
        conectar(config.getRabbitHost(), config.getRabbitPuerto(), config.getRabbitUsuario(), config.getRabbitClave());
    }

    public synchronized void conectar(String host, int puerto, String usuario, String clave) {
        if (conexion != null) {
            throw new IllegalStateException("El cliente de RabbitMQ ya está conectado");
        }
        ConnectionFactory fabrica = new ConnectionFactory();
        fabrica.setHost(host);
        fabrica.setPort(puerto);
        if (usuario != null) {
            fabrica.setUsername(usuario);
        }
        if (clave != null) {
            fabrica.setPassword(clave);
        }
        // Si se cae la red, la librería reconecta sola y vuelve a declarar la cola.
        fabrica.setAutomaticRecoveryEnabled(true);
        try {
            conexion = fabrica.newConnection("convitec-" + codigoSede);
            canal = conexion.createChannel();
            canal.exchangeDeclare(EXCHANGE, BuiltinExchangeType.FANOUT, true);
            String cola = nombreCola(codigoSede);
            canal.queueDeclare(cola, true, false, false, null);
            canal.queueBind(cola, EXCHANGE, "");
            canal.basicConsume(cola, true,
                    (etiqueta, entrega) -> recibir(entrega.getBody()),
                    etiqueta -> { });
        } catch (IOException | TimeoutException e) {
            cerrar();
            throw new IllegalStateException("No se pudo conectar a RabbitMQ en " + host + ":" + puerto
                    + " con el usuario " + usuario + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void enviar(Mensaje mensaje) {
        if (mensaje == null || mensaje.getTipo() == null) {
            throw new IllegalArgumentException("El mensaje debe tener tipo");
        }
        byte[] cuerpo = gson.toJson(mensaje).getBytes(StandardCharsets.UTF_8);
        synchronized (this) {
            if (canal == null) {
                throw new IllegalStateException("El cliente de RabbitMQ no está conectado");
            }
            try {
                canal.basicPublish(EXCHANGE, "", MessageProperties.PERSISTENT_TEXT_PLAIN, cuerpo);
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo enviar el mensaje " + mensaje.getTipo() + ": "
                        + e.getMessage(), e);
            }
        }
    }

    @Override
    public synchronized void alRecibir(ManejadorMensajes manejador) {
        if (manejador == null) {
            throw new IllegalArgumentException("El manejador no puede ser null");
        }
        manejadores.agregar(manejador);
    }

    /** Llega un mensaje de la cola: se interpreta y, si es para esta sede, se reparte a los manejadores. */
    void recibir(byte[] cuerpo) {
        Mensaje mensaje = interpretar(cuerpo, codigoSede, gson);
        if (mensaje == null) {
            return;
        }
        ManejadorMensajes[] copia;
        synchronized (this) {
            copia = manejadores.toArray(new ManejadorMensajes[0]);
        }
        for (ManejadorMensajes manejador : copia) {
            try {
                manejador.manejar(mensaje);
            } catch (RuntimeException e) {
                // Un mensaje que falla no debe tumbar al consumidor de la cola.
                System.err.println("Aviso: error procesando " + mensaje.getTipo() + " de " + mensaje.getDe()
                        + ": " + e.getMessage());
            }
        }
    }

    /**
     * Convierte el cuerpo recibido en un Mensaje, o devuelve null si hay que ignorarlo:
     * JSON roto, tipo desconocido, sin remitente, enviado por esta misma sede o para otra sede.
     */
    public static Mensaje interpretar(byte[] cuerpo, String codigoSede, Gson gson) {
        if (cuerpo == null) {
            return null;
        }
        Mensaje mensaje;
        try {
            mensaje = gson.fromJson(new String(cuerpo, StandardCharsets.UTF_8), Mensaje.class);
        } catch (JsonParseException e) {
            System.err.println("Aviso: se ignoró un mensaje con JSON roto");
            return null;
        }
        if (mensaje == null || mensaje.getTipo() == null || mensaje.getDe() == null) {
            return null;
        }
        if (mensaje.getDe().equals(codigoSede) || !mensaje.esPara(codigoSede)) {
            return null;
        }
        return mensaje;
    }

    /** Cierra el canal y la conexión. Se puede llamar aunque no se haya conectado. */
    public synchronized void cerrar() {
        try {
            if (canal != null && canal.isOpen()) {
                canal.close();
            }
        } catch (IOException | TimeoutException | RuntimeException ignorado) {
            // Al cerrar no importa si ya estaba caído.
        }
        try {
            if (conexion != null && conexion.isOpen()) {
                conexion.close();
            }
        } catch (IOException | RuntimeException ignorado) {
            // Igual que arriba.
        }
        canal = null;
        conexion = null;
    }
}
