package convitec.servicios;

/**
 * Pregunta a la parte de rutas (P2) si se puede llegar a un destino desde esta sede.
 *
 * Cuando Dijkstra esté listo, en App basta:
 * <pre>
 *   VerificadorRutas rutas = destino -> Dijkstra.calcular(grafo, sede.getUbicacion()).alcanzable(destino);
 * </pre>
 * Mientras tanto se usa {@link RutasFalsas}.
 */
@FunctionalInterface
public interface VerificadorRutas {
    boolean esAlcanzable(String destino);
}
