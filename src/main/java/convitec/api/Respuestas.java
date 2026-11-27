package convitec.api;

import convitec.modelo.NodoCatalogo;
import convitec.modelo.Producto;
import convitec.modelo.Venta;
import convitec.servicios.EstadisticasService;
import convitec.servicios.ResumenProducto;

/**
 * Objetos que se convierten a JSON en las respuestas y en los cuerpos de las solicitudes.
 * Solo usan arreglos (nada de colecciones del lenguaje).
 */
final class Respuestas {

    private Respuestas() {
    }

    /** Cuerpo de todo error: { "error": "..." }. */
    static class Error {
        final String error;

        Error(String error) {
            this.error = error;
        }
    }

    /** Respuesta corta de éxito: { "ok": true, "mensaje": "..." }. */
    static class Ok {
        final boolean ok = true;
        final String mensaje;

        Ok(String mensaje) {
            this.mensaje = mensaje;
        }
    }

    /** Una fila del inventario local: el producto y la existencia de esta sede. */
    static class ItemInventario {
        final String codigo;
        final String nombre;
        final String descripcion;
        final String categoria;
        final double precio;
        final int existencia;

        ItemInventario(NodoCatalogo nodo, int indiceSede) {
            Producto p = nodo.getProducto();
            this.codigo = p.getCodigo();
            this.nombre = p.getNombre();
            this.descripcion = p.getDescripcion();
            this.categoria = p.getCategoria();
            this.precio = p.getPrecio();
            this.existencia = nodo.getExistencia(indiceSede);
        }
    }

    /** Un pedido entregado con su rendimiento ya calculado (en Venta es un método y Gson no lo ve). */
    static class PeorPedido {
        final String pedidoId;
        final String fecha;
        final double montoTotal;
        final int tiempoEntrega;
        final double rendimiento;

        PeorPedido(Venta venta) {
            this.pedidoId = venta.getPedidoId();
            this.fecha = venta.getFecha();
            this.montoTotal = venta.getMontoTotal();
            this.tiempoEntrega = venta.getTiempoEntrega();
            this.rendimiento = venta.getRendimiento();
        }
    }

    /** Respuesta de GET /api/estadisticas. */
    static class Estadisticas {
        final ResumenProducto estrella;
        final double ingresosTotales;
        final int cantidadVentas;
        final ResumenProducto[] masVendidos;
        final PeorPedido[] peoresPedidos;

        Estadisticas(EstadisticasService servicio, int cantidadVentas) {
            ResumenProducto[] porIngresos = servicio.rankingPorIngresos();
            this.estrella = porIngresos.length == 0 ? null : porIngresos[0];
            this.ingresosTotales = servicio.ingresosTotales();
            this.cantidadVentas = cantidadVentas;
            this.masVendidos = servicio.rankingPorUnidades();
            Venta[] peores = servicio.peoresPedidos();
            this.peoresPedidos = new PeorPedido[peores.length];
            for (int i = 0; i < peores.length; i++) {
                this.peoresPedidos[i] = new PeorPedido(peores[i]);
            }
        }
    }

    /** Cuerpo de POST /api/inventario/{codigo}: { "cantidad": 10 }. */
    static class SolicitudUnidades {
        Integer cantidad;
    }

    /** Cuerpo de POST /api/pedidos. */
    static class SolicitudPedido {
        String destino;
        boolean mostrador;
        Linea[] items;

        static class Linea {
            String codigo;
            Integer cantidad;
        }
    }
}
