package convitec.modelo;

/** Estados de un pedido: RESERVANDO → PENDIENTE → EN_RUTA → ENTREGADO, o CANCELADO. */
public enum EstadoPedido {
    RESERVANDO,
    PENDIENTE,
    EN_RUTA,
    ENTREGADO,
    CANCELADO
}
