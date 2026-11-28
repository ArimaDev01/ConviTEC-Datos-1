package convitec.grafo;

/** Resultado de caminos mínimos desde un origen, sin exponer sus arreglos internos. */
public class ResultadoDijkstra {

    /** Distancia de un destino inalcanzable o inexistente; ninguna distancia válida es negativa. */
    public static final int INALCANZABLE = -1;

    private final String[] nombres;
    private final int origen;
    private final int[] distancias;
    private final int[] predecesores;

    /** Uso interno de Dijkstra; copia los arreglos para no conservar referencias modificables ajenas. */
    ResultadoDijkstra(String[] nombres, int origen, int[] distancias, int[] predecesores) {
        this.origen = origen;
        this.nombres = new String[nombres.length];
        this.distancias = new int[distancias.length];
        this.predecesores = new int[predecesores.length];
        for (int i = 0; i < nombres.length; i++) {
            this.nombres[i] = nombres[i];
            this.distancias[i] = distancias[i];
            this.predecesores[i] = predecesores[i];
        }
    }

    /**
     * Devuelve el mínimo en minutos, incluido 0 para el origen e Integer.MAX_VALUE
     * si ese es el mínimo exacto. Devuelve INALCANZABLE para destinos desconectados,
     * inexistentes, null o en blanco. El nombre se compara exactamente, con sus tildes.
     */
    public int distancia(String destino) {
        int indice = indiceDe(destino);
        return indice == -1 ? INALCANZABLE : distancias[indice];
    }

    /** Indica si existe un camino; devuelve false para destinos inexistentes, null o en blanco. */
    public boolean alcanzable(String destino) {
        return distancia(destino) != INALCANZABLE;
    }

    /**
     * Devuelve un arreglo nuevo con el origen, los intermediarios y el destino, en orden.
     * Para el origen devuelve un solo nombre. Para destinos inalcanzables, inexistentes,
     * null o en blanco devuelve un arreglo vacío, nunca un camino inventado ni null.
     */
    public String[] camino(String destino) {
        int indice = indiceDe(destino);
        if (indice == -1 || distancias[indice] == INALCANZABLE) {
            return new String[0];
        }

        // Primero contamos la cadena para reservar solo las posiciones necesarias.
        int longitud = 1;
        for (int actual = indice; actual != origen; actual = predecesores[actual]) {
            longitud++;
        }

        // La cadena se recorre hacia atrás, pero el arreglo se llena de derecha a izquierda.
        String[] camino = new String[longitud];
        int actual = indice;
        for (int i = longitud - 1; i >= 0; i--) {
            camino[i] = nombres[actual];
            actual = predecesores[actual];
        }
        return camino;
    }

    private int indiceDe(String destino) {
        for (int i = 0; i < nombres.length; i++) {
            if (nombres[i].equals(destino)) {
                return i;
            }
        }
        return -1;
    }
}
