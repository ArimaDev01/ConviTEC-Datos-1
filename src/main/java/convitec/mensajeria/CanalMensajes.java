package convitec.mensajeria;

import convitec.modelo.Mensaje;

/**
 * Forma de hablar con las demás sedes. La implementación real es ClienteRabbit;
 * en las pruebas se usa un bus en memoria, así los servicios se prueban sin RabbitMQ.
 *
 * Quien implemente esta interfaz entrega a los manejadores solo los mensajes que son
 * para esta sede (para = su código o TODOS), nunca los que ella misma envió.
 */
public interface CanalMensajes {
    /** Envía el mensaje a todas las sedes; cada una decide si es para ella. */
    void enviar(Mensaje mensaje);

    /** Registra quién procesa los mensajes que llegan. Se pueden registrar varios. */
    void alRecibir(ManejadorMensajes manejador);
}
