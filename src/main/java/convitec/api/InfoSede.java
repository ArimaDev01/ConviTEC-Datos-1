package convitec.api;

import convitec.modelo.Sede;

/**
 * Respuesta de GET /api/sede: la página la pide al cargar para saber en qué sede está,
 * cómo se llaman las columnas del catálogo global y qué destinos ofrecer en "Nuevo pedido".
 */
public class InfoSede {
    private final String codigo;
    private final String nombre;
    private final String ubicacion;
    private final int indice;
    private final Sede[] sedes;
    private final String[] destinos;
    private final String horaVirtual;
    private final boolean datosFalsos;

    public InfoSede(Sede sede, int indice, Sede[] sedes, String[] destinos, String horaVirtual, boolean datosFalsos) {
        this.codigo = sede.getCodigo();
        this.nombre = sede.getNombre();
        this.ubicacion = sede.getUbicacion();
        this.indice = indice;
        this.sedes = sedes;
        this.destinos = destinos;
        this.horaVirtual = horaVirtual;
        this.datosFalsos = datosFalsos;
    }

    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public String getUbicacion() { return ubicacion; }
    public int getIndice() { return indice; }
    public Sede[] getSedes() { return sedes; }
    public String[] getDestinos() { return destinos; }
    public String getHoraVirtual() { return horaVirtual; }
    public boolean isDatosFalsos() { return datosFalsos; }
}
