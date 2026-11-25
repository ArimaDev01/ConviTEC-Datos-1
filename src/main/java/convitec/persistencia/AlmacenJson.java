package convitec.persistencia;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Guarda y carga los datos de una sede en archivos JSON dentro de su carpeta
 * (por ejemplo data/ALA/pedidos.json). Siempre trabaja con arreglos, nunca con
 * colecciones del lenguaje: cada servicio convierte su estructura con toArray()
 * antes de guardar y la reconstruye a partir del arreglo al cargar.
 *
 * Uso:
 * <pre>
 *   AlmacenJson almacen = new AlmacenJson("data/ALA");
 *   almacen.guardar("pedidos.json", historial.toArray(new Pedido[0]));
 *   Pedido[] pedidos = almacen.cargar("pedidos.json", Pedido[].class);
 * </pre>
 */
public class AlmacenJson {

    private final Path carpeta;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /** La carpeta se crea si no existe. */
    public AlmacenJson(String carpetaSede) {
        if (carpetaSede == null || carpetaSede.isBlank()) {
            throw new IllegalArgumentException("Hay que indicar la carpeta de la sede");
        }
        this.carpeta = Path.of(carpetaSede);
        try {
            Files.createDirectories(carpeta);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear la carpeta " + carpeta, e);
        }
    }

    /**
     * Reescribe el archivo completo con los datos. Primero escribe un archivo temporal
     * y después lo renombra, así un cierre a medio guardar no deja el JSON dañado.
     */
    public <T> void guardar(String archivo, T[] datos) {
        if (datos == null) {
            throw new IllegalArgumentException("No se puede guardar null en " + archivo);
        }
        Path destino = carpeta.resolve(archivo);
        Path temporal = null;
        try {
            // Nombre único: dos hilos guardando el mismo archivo no se pisan el temporal.
            temporal = Files.createTempFile(carpeta, archivo, ".tmp");
            Files.writeString(temporal, gson.toJson(datos), StandardCharsets.UTF_8);
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            borrarSinFallar(temporal);
            throw new UncheckedIOException("No se pudo guardar " + destino, e);
        }
    }

    /**
     * Lee el arreglo guardado. Si el archivo no existe o está vacío, devuelve un
     * arreglo vacío para que la sede pueda arrancar igual. Si está dañado o no se
     * puede leer (por ejemplo, no está en UTF-8), lo renombra a "archivo.danado",
     * avisa en consola y también devuelve un arreglo vacío.
     */
    @SuppressWarnings("unchecked")
    public <T> T[] cargar(String archivo, Class<T[]> tipo) {
        T[] vacio = (T[]) Array.newInstance(tipo.getComponentType(), 0);
        Path origen = carpeta.resolve(archivo);
        if (!Files.exists(origen)) {
            return vacio;
        }
        try {
            T[] datos = gson.fromJson(Files.readString(origen, StandardCharsets.UTF_8), tipo);
            return datos == null ? vacio : datos;
        } catch (JsonParseException | IOException e) {
            // Se aparta el archivo para que el próximo guardar no lo sobrescriba y se pueda rescatar a mano.
            Path apartado = carpeta.resolve(archivo + ".danado");
            System.err.println("Aviso: no se pudo leer " + origen + "; se movió a " + apartado + " (" + e.getMessage() + ")");
            try {
                Files.move(origen, apartado, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException errorAlMover) {
                System.err.println("Aviso: no se pudo apartar " + origen + " (" + errorAlMover.getMessage() + ")");
            }
            return vacio;
        }
    }

    private static void borrarSinFallar(Path archivo) {
        if (archivo == null) {
            return;
        }
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException ignorado) {
            // Si no se puede borrar el temporal, el error importante ya se está reportando.
        }
    }

    /** True si el archivo ya fue guardado alguna vez (sirve para saber si es el primer arranque). */
    public boolean existe(String archivo) {
        return Files.exists(carpeta.resolve(archivo));
    }
}
