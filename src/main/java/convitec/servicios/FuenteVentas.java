package convitec.servicios;

import convitec.modelo.Venta;

/**
 * De dónde saca EstadisticasService las ventas de la sede.
 * Con el servicio real basta {@code pedidoService::ventas}; en las pruebas, una lambda con un arreglo fijo.
 */
public interface FuenteVentas {
    Venta[] ventas();
}
