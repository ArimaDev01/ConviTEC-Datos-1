package convitec.grafo;

/** Calcula caminos mínimos con la matriz del grafo, en tiempo O(V²) y espacio adicional O(V). */
public class Dijkstra {

    private Dijkstra() {
    }

    /**
     * Calcula las distancias y los predecesores desde una ubicación existente.
     * La distancia -1 representa infinito mientras no se conoce un camino.
     * En empates se procesa primero el índice menor y se conserva el primer predecesor.
     *
     * @throws IllegalArgumentException si el grafo es null o el origen no existe,
     *         es null o está en blanco; Grafo.cargar no permite grafos vacíos
     * @throws ArithmeticException si la distancia mínima a algún vértice alcanzable
     *         supera Integer.MAX_VALUE; no se devuelven resultados parciales
     */
    public static ResultadoDijkstra calcular(Grafo g, String origen) {
        if (g == null) {
            throw new IllegalArgumentException("El grafo no puede ser null");
        }
        int indiceOrigen = g.indiceDe(origen);
        if (indiceOrigen < 0) {
            throw new IllegalArgumentException("El origen '" + origen + "' no existe en el grafo");
        }

        String[] nombres = g.lugares();
        int cantidad = nombres.length;
        int[] dist = new int[cantidad];
        int[] prev = new int[cantidad];
        boolean[] visitado = new boolean[cantidad];
        for (int i = 0; i < cantidad; i++) {
            dist[i] = ResultadoDijkstra.INALCANZABLE;
            prev[i] = -1;
        }
        dist[indiceOrigen] = 0;

        for (int paso = 0; paso < cantidad; paso++) {
            int actual = seleccionarMenorDistancia(dist, visitado);
            if (actual == -1) {
                break;
            }
            visitado[actual] = true;

            for (int destino = 0; destino < cantidad; destino++) {
                if (visitado[destino]) {
                    continue;
                }
                int peso = g.peso(actual, destino);
                if (peso == Grafo.SIN_CONEXION) {
                    continue;
                }

                // actual siempre tiene distancia finita; long evita desbordar al sumar.
                long candidata = (long) dist[actual] + peso;
                if (candidata > Integer.MAX_VALUE) {
                    // Puede existir otra ruta más corta; no abortamos por este candidato.
                    continue;
                }
                if (dist[destino] == ResultadoDijkstra.INALCANZABLE || candidata < dist[destino]) {
                    dist[destino] = (int) candidata;
                    prev[destino] = actual;
                }
            }
        }

        comprobarDistanciasRepresentables(g, origen, dist);
        return new ResultadoDijkstra(nombres, indiceOrigen, dist, prev);
    }

    /** Excluye los infinitos de la comparación, pues su representación numérica es -1. */
    private static int seleccionarMenorDistancia(int[] dist, boolean[] visitado) {
        int menor = -1;
        for (int i = 0; i < dist.length; i++) {
            if (!visitado[i] && dist[i] != ResultadoDijkstra.INALCANZABLE
                    && (menor == -1 || dist[i] < dist[menor])) {
                menor = i;
            }
        }
        return menor;
    }

    /**
     * Al terminar, una arista desde un vértice con distancia finita hacia otro aún
     * sin distancia demuestra que este último es alcanzable, pero su mínimo no cabe
     * en int. Esta comprobación O(V²) evita confundir desbordamiento con desconexión.
     */
    private static void comprobarDistanciasRepresentables(Grafo grafo, String origen, int[] dist) {
        for (int a = 0; a < dist.length; a++) {
            if (dist[a] == ResultadoDijkstra.INALCANZABLE) {
                continue;
            }
            for (int b = 0; b < dist.length; b++) {
                if (dist[b] == ResultadoDijkstra.INALCANZABLE
                        && grafo.peso(a, b) != Grafo.SIN_CONEXION) {
                    throw new ArithmeticException("La distancia mínima desde '" + origen + "' hasta '"
                            + grafo.nombreDe(b) + "' supera Integer.MAX_VALUE minutos");
                }
            }
        }
    }
}
