package convitec.api;

import convitec.modelo.LineaPedido;
import convitec.modelo.NodoCatalogo;
import convitec.modelo.PasoRecorrido;
import convitec.modelo.Pedido;
import convitec.modelo.Recorrido;
import convitec.modelo.ResultadoPedido;
import convitec.modelo.Venta;

/**
 * Todo lo que la API necesita de una sede. ServidorRest solo conoce esta interfaz,
 * así se puede arrancar con DatosFalsos mientras P2 y P3 terminan sus servicios,
 * y después cambiar a los servicios reales sin tocar las rutas.
 *
 * Los métodos que fallan por una entrada inválida lanzan IllegalArgumentException
 * con un mensaje para el dependiente; la API lo devuelve como HTTP 400.
 */
public interface DatosSede {

    /** Código, nombre y ubicación de la sede, las demás sedes y los destinos posibles. */
    InfoSede info();

    /** Catálogo global: cada producto con su existencia en todas las sedes, ordenado por código. */
    NodoCatalogo[] catalogo();

    /** Suma unidades al inventario local. Rechaza cantidad &lt;= 0 y productos inexistentes. */
    void agregarUnidades(String codigo, int cantidad);

    /** Valida existencias y ruta; si algo falla, el resultado viene con ok = false y el motivo. */
    ResultadoPedido registrarPedido(String destino, boolean mostrador, LineaPedido[] items);

    /** Historial: todos los pedidos de la sede, en cualquier estado. */
    Pedido[] pedidos();

    /** Pedidos confirmados que esperan entrega, en orden de llegada. */
    Pedido[] pendientes();

    /** Calcula y hace la siguiente entrega del recorrido actual; null si no hay pendientes alcanzables. */
    PasoRecorrido siguientePaso();

    /** Recorridos de entrega, el más reciente al final. */
    Recorrido[] recorridos();

    /** Historial de ventas de la sede. */
    Venta[] ventas();
}
