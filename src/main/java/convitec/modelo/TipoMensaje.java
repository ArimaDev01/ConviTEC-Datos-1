package convitec.modelo;

/** Los seis tipos de mensaje entre sedes (sección 7 de PLAN.md). */
public enum TipoMensaje {
    /** Aviso a todas: mi existencia de un producto ahora es N. */
    STOCK,
    /** Una sede acaba de arrancar y pide la existencia de las demás. */
    HOLA,
    /** Pedir a la sede dueña que aparte unidades de un producto. */
    RESERVAR,
    /** Respuesta a RESERVAR: aceptada o rechazada con motivo. */
    RESPUESTA,
    /** El pedido se confirmó: la reserva queda rebajada. */
    CONFIRMAR,
    /** El pedido se canceló: devolver lo reservado. */
    LIBERAR
}
