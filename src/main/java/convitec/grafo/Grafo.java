package convitec.grafo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * Grafo no dirigido y ponderado, construido con arreglos y una matriz de adyacencia.
 * Los nombres se conservan exactamente y reciben índices según su primera aparición
 * en el JSON. La diagonal vale 0; una conexión directa inexistente vale SIN_CONEXION.
 * No se agregan conexiones entre componentes desconectados.
 */
public class Grafo {

    /** Ausencia de una conexión directa; no representa una distancia calculada. */
    public static final int SIN_CONEXION = -1;

    private final String[] nombres;
    private final int[][] matriz;

    /** Gson convierte el arreglo JSON directamente en un RutaJson[]. */
    private static class RutaJson {
        String origen;
        String destino;
        int tiempo;
    }

    private Grafo(String[] nombres) {
        this.nombres = nombres;
        this.matriz = new int[nombres.length][nombres.length];
        for (int i = 0; i < nombres.length; i++) {
            for (int j = 0; j < nombres.length; j++) {
                matriz[i][j] = i == j ? 0 : SIN_CONEXION;
            }
        }
    }

    /**
     * Lee un arreglo de rutas con origen, destino y tiempo entero positivo en minutos.
     * El campo adicional "ruta" del archivo no interviene en la matriz.
     * Las conexiones repetidas, incluso en sentido contrario, conservan el menor tiempo.
     *
     * @throws IllegalArgumentException si la ruta del archivo es nula, está en blanco o es inválida
     * @throws IllegalStateException si el archivo no se puede leer, no contiene rutas,
     *         su JSON es inválido o alguna ruta tiene nombres nulos/en blanco,
     *         tiempo no positivo o el mismo origen y destino
     */
    public static Grafo cargar(String rutaJson) {
        if (rutaJson == null || rutaJson.isBlank()) {
            throw new IllegalArgumentException("La ruta del archivo JSON no puede estar vacía");
        }
        RutaJson[] rutas = leerRutas(Path.of(rutaJson));

        // Cada ruta puede aportar, como máximo, dos ubicaciones nuevas.
        String[] temporales = new String[rutas.length * 2];
        int cantidad = 0;
        for (int i = 0; i < rutas.length; i++) {
            RutaJson ruta = rutas[i];
            validarRuta(ruta, i);
            if (buscarIndice(temporales, cantidad, ruta.origen) == -1) {
                temporales[cantidad++] = ruta.origen;
            }
            if (buscarIndice(temporales, cantidad, ruta.destino) == -1) {
                temporales[cantidad++] = ruta.destino;
            }
        }

        String[] nombres = new String[cantidad];
        for (int i = 0; i < cantidad; i++) {
            nombres[i] = temporales[i];
        }

        Grafo grafo = new Grafo(nombres);
        for (RutaJson ruta : rutas) {
            int origen = grafo.indiceDe(ruta.origen);
            int destino = grafo.indiceDe(ruta.destino);
            int anterior = grafo.matriz[origen][destino];
            if (anterior == SIN_CONEXION || ruta.tiempo < anterior) {
                grafo.matriz[origen][destino] = ruta.tiempo;
                grafo.matriz[destino][origen] = ruta.tiempo;
            }
        }
        return grafo;
    }

    /** Devuelve el índice del nombre exacto, o -1 si no existe (incluido null o blanco). */
    public int indiceDe(String lugar) {
        return buscarIndice(nombres, nombres.length, lugar);
    }

    /**
     * Devuelve el nombre asociado al índice.
     * @throws IndexOutOfBoundsException si el índice no pertenece al grafo
     */
    public String nombreDe(int i) {
        validarIndice(i);
        return nombres[i];
    }

    /** Devuelve una copia del arreglo para proteger los nombres y sus índices. */
    public String[] lugares() {
        String[] copia = new String[nombres.length];
        for (int i = 0; i < nombres.length; i++) {
            copia[i] = nombres[i];
        }
        return copia;
    }

    /**
     * Devuelve el tiempo de la conexión directa, SIN_CONEXION si no existe,
     * o 0 si ambos índices representan el mismo vértice. No calcula caminos.
     * @throws IndexOutOfBoundsException si alguno de los índices no pertenece al grafo
     */
    public int peso(int a, int b) {
        validarIndice(a);
        validarIndice(b);
        return matriz[a][b];
    }

    private static RutaJson[] leerRutas(Path archivo) {
        try {
            String contenido = Files.readString(archivo, StandardCharsets.UTF_8);
            if (contenido.isBlank()) {
                throw new IllegalStateException("El archivo de rutas " + archivo + " está vacío");
            }
            Gson gson = new GsonBuilder().setStrictness(Strictness.STRICT).create();
            RutaJson[] rutas = gson.fromJson(contenido, RutaJson[].class);
            if (rutas == null || rutas.length == 0) {
                throw new IllegalStateException("El archivo " + archivo + " no contiene rutas");
            }
            return rutas;
        } catch (NoSuchFileException e) {
            throw new IllegalStateException("Falta el archivo de rutas " + archivo, e);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el archivo de rutas " + archivo, e);
        } catch (JsonParseException e) {
            throw new IllegalStateException("El archivo de rutas " + archivo
                    + " no es JSON válido para un arreglo de rutas: " + e.getMessage(), e);
        }
    }

    private static void validarRuta(RutaJson ruta, int indice) {
        String descripcion = "La ruta #" + (indice + 1);
        if (ruta == null || ruta.origen == null || ruta.origen.isBlank()
                || ruta.destino == null || ruta.destino.isBlank()) {
            throw new IllegalStateException(descripcion + " debe tener origen y destino no vacíos");
        }
        if (ruta.tiempo <= 0) {
            throw new IllegalStateException(descripcion + " debe tener un tiempo entero positivo en minutos");
        }
        if (ruta.origen.equals(ruta.destino)) {
            throw new IllegalStateException(descripcion + " no puede conectar una ubicación consigo misma");
        }
    }

    /** Busca solo en las casillas ocupadas; las restantes pueden ser null. */
    private static int buscarIndice(String[] lugares, int cantidad, String lugar) {
        for (int i = 0; i < cantidad; i++) {
            if (lugares[i].equals(lugar)) {
                return i;
            }
        }
        return -1;
    }

    private void validarIndice(int indice) {
        if (indice < 0 || indice >= nombres.length) {
            throw new IndexOutOfBoundsException("Índice de ubicación fuera de rango: " + indice
                    + ". Cantidad de ubicaciones: " + nombres.length);
        }
    }
}
