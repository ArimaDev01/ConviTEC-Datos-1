package convitec.servicios;

/**
 * Versión falsa temporal de {@link VerificadorRutas}, mientras P2 termina Dijkstra:
 * todo destino es alcanzable menos Liberia y Cañas (Guanacaste está aislado del resto).
 * Se borra cuando App use el Dijkstra real.
 */
public class RutasFalsas implements VerificadorRutas {
    @Override
    public boolean esAlcanzable(String destino) {
        return destino != null && !destino.isBlank() && !destino.equals("Liberia") && !destino.equals("Cañas");
    }
}
