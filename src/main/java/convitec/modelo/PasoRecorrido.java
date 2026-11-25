package convitec.modelo;

/**
 * Un tramo de un recorrido de entrega: de dónde a dónde, por qué lugares pasa,
 * cuántos minutos tarda y qué pedidos se entregan al llegar.
 */
public class PasoRecorrido {
    private String desde;
    private String hasta;
    private String[] camino;
    private int minutos;
    private int tiempoAcumulado;
    private String[] pedidosEntregados;

    public PasoRecorrido() {
    }

    public PasoRecorrido(String desde, String hasta, String[] camino, int minutos, int tiempoAcumulado,
                         String[] pedidosEntregados) {
        this.desde = desde;
        this.hasta = hasta;
        this.camino = camino;
        this.minutos = minutos;
        this.tiempoAcumulado = tiempoAcumulado;
        this.pedidosEntregados = pedidosEntregados;
    }

    public String getDesde() { return desde; }
    public String getHasta() { return hasta; }
    public String[] getCamino() { return camino; }
    public int getMinutos() { return minutos; }
    public int getTiempoAcumulado() { return tiempoAcumulado; }
    public String[] getPedidosEntregados() { return pedidosEntregados; }
}
