package convitec.servicios;

import convitec.estructuras.ArbolAVL;
import convitec.estructuras.Comparador;
import convitec.estructuras.Ordenamiento;
import convitec.modelo.LineaPedido;
import convitec.modelo.Producto;
import convitec.modelo.Venta;

/**
 * Estadísticas de ventas de una sede: producto estrella, más vendidos y peores pedidos.
 *
 * Cada consulta recalcula desde las ventas actuales, así siempre refleja lo último que pasó:
 * 1. Se agrupan las líneas de todas las ventas por código de producto en un ArbolAVL
 *    (buscar e insertar O(log p), con p productos distintos).
 * 2. Los totales se ordenan con el mergeSort propio, que es estable: en un empate
 *    se conserva el orden que traía el arreglo (por código, porque sale del AVL en orden).
 */
public class EstadisticasService {

    /** Más ingresos primero; si empatan, más unidades primero. */
    private static final Comparador<ResumenProducto> POR_INGRESOS = (a, b) -> {
        int porIngresos = Double.compare(b.getIngresos(), a.getIngresos());
        return porIngresos != 0 ? porIngresos : Integer.compare(b.getUnidades(), a.getUnidades());
    };

    /** Más unidades primero; si empatan, más ingresos primero. */
    private static final Comparador<ResumenProducto> POR_UNIDADES = (a, b) -> {
        int porUnidades = Integer.compare(b.getUnidades(), a.getUnidades());
        return porUnidades != 0 ? porUnidades : Double.compare(b.getIngresos(), a.getIngresos());
    };

    /** Menor rendimiento primero (el peor pedido queda en la posición 0). */
    private static final Comparador<Venta> POR_RENDIMIENTO =
            (a, b) -> Double.compare(a.getRendimiento(), b.getRendimiento());

    private final FuenteVentas fuente;

    public EstadisticasService(FuenteVentas fuente) {
        if (fuente == null) {
            throw new IllegalArgumentException("La fuente de ventas no puede ser null");
        }
        this.fuente = fuente;
    }

    /** Producto con más ingresos (unidades × precio). null si todavía no hay ventas. */
    public Producto estrella() {
        ResumenProducto[] ranking = rankingPorIngresos();
        return ranking.length == 0 ? null : ranking[0].comoProducto();
    }

    /** Productos de más a menos unidades vendidas. Vacío si no hay ventas. */
    public Producto[] masVendidos() {
        ResumenProducto[] ranking = rankingPorUnidades();
        Producto[] productos = new Producto[ranking.length];
        for (int i = 0; i < ranking.length; i++) {
            productos[i] = ranking[i].comoProducto();
        }
        return productos;
    }

    /**
     * Ventas entregadas de menor a mayor rendimiento (monto / tiempo de entrega).
     * Quedan fuera las de mostrador y las de tiempo 0 (aún no entregadas), para no dividir entre 0.
     */
    public Venta[] peoresPedidos() {
        Venta[] ventas = ventasSeguras();
        int cantidad = 0;
        for (Venta venta : ventas) {
            if (venta != null && venta.tieneRendimiento()) {
                cantidad++;
            }
        }
        Venta[] conRendimiento = new Venta[cantidad];
        int i = 0;
        for (Venta venta : ventas) {
            if (venta != null && venta.tieneRendimiento()) {
                conRendimiento[i++] = venta;
            }
        }
        Ordenamiento.mergeSort(conRendimiento, POR_RENDIMIENTO);
        return conRendimiento;
    }

    /** Totales por producto, de más a menos ingresos. Lo usa la página para mostrar los montos. */
    public ResumenProducto[] rankingPorIngresos() {
        ResumenProducto[] resumen = agruparPorProducto();
        Ordenamiento.mergeSort(resumen, POR_INGRESOS);
        return resumen;
    }

    /** Totales por producto, de más a menos unidades. */
    public ResumenProducto[] rankingPorUnidades() {
        ResumenProducto[] resumen = agruparPorProducto();
        Ordenamiento.mergeSort(resumen, POR_UNIDADES);
        return resumen;
    }

    /** Ingresos totales de la sede (suma de los montos de todas las ventas). */
    public double ingresosTotales() {
        double total = 0;
        for (Venta venta : ventasSeguras()) {
            if (venta != null) {
                total += venta.getMontoTotal();
            }
        }
        return total;
    }

    /** Recorre todas las líneas de todas las ventas y acumula por código en el AVL. */
    private ResumenProducto[] agruparPorProducto() {
        ArbolAVL<ResumenProducto> porCodigo = new ArbolAVL<>();
        for (Venta venta : ventasSeguras()) {
            if (venta == null || venta.getLineas() == null) {
                continue;
            }
            for (LineaPedido linea : venta.getLineas()) {
                if (linea == null || linea.getCodigo() == null || linea.getCantidad() <= 0) {
                    continue;
                }
                ResumenProducto resumen = porCodigo.buscar(linea.getCodigo());
                if (resumen == null) {
                    resumen = new ResumenProducto(linea.getCodigo(), linea.getNombre());
                    porCodigo.insertar(linea.getCodigo(), resumen);
                }
                resumen.sumar(linea.getCantidad(), linea.getPrecioUnitario());
            }
        }
        return porCodigo.enOrden(new ResumenProducto[0]);
    }

    private Venta[] ventasSeguras() {
        Venta[] ventas = fuente.ventas();
        return ventas == null ? new Venta[0] : ventas;
    }
}
