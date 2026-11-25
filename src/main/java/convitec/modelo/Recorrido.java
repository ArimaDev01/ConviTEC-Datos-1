package convitec.modelo;

/** Recorrido de entrega de una sede: los pasos hechos y el tiempo total. */
public class Recorrido {
    private String sede;
    private String horaSalida;
    private PasoRecorrido[] pasos;
    private int tiempoTotal;
    private boolean terminado;

    public Recorrido() {
    }

    public Recorrido(String sede, String horaSalida) {
        this.sede = sede;
        this.horaSalida = horaSalida;
        this.pasos = new PasoRecorrido[0];
    }

    public String getSede() { return sede; }
    public String getHoraSalida() { return horaSalida; }
    public PasoRecorrido[] getPasos() { return pasos; }
    public int getTiempoTotal() { return tiempoTotal; }
    public boolean isTerminado() { return terminado; }

    public void setPasos(PasoRecorrido[] pasos) { this.pasos = pasos; }
    public void setTiempoTotal(int tiempoTotal) { this.tiempoTotal = tiempoTotal; }
    public void setTerminado(boolean terminado) { this.terminado = terminado; }
}
