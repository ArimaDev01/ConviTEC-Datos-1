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
        Path temporal = carpeta.resolve(archivo + ".tmp");
        try {
            Files.writeString(temporal, gson.toJson(datos), StandardCharsets.UTF_8);
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar " + destino, e);
        }
    }

    /**
     * Lee el arreglo guardado. Si el archivo no existe, está vacío o está dañado,
     * devuelve un arreglo vacío (y avisa en consola si estaba dañado), para que
     * la sede pueda arrancar igual.
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
        } catch (JsonParseException e) {
            System.err.println("Aviso: " + origen + " está dañado y se ignora (" + e.getMessage() + ")");
            return vacio;
        } catch (IOException e) {
            System.err.println("Aviso: no se pudo leer " + origen + " (" + e.getMessage() + ")");
            return vacio;
        }
    }

    /** True si el archivo ya fue guardado alguna vez (sirve para saber si es el primer arranque). */
    public boolean existe(String archivo) {
        return Files.exists(carpeta.resolve(archivo));
    }
}
