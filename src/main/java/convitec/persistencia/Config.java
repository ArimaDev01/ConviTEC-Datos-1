package convitec.persistencia;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import convitec.modelo.NodoCatalogo;
import convitec.modelo.Producto;
import convitec.modelo.Sede;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuración de una sede, leída de la carpeta config/ al arrancar.
 * A diferencia de los datos, la configuración es obligatoria: si falta o está mal,
 * se lanza una excepción con un mensaje claro y la sede no arranca.
 *
 * Formato de los archivos:
 * <pre>
 * sedes.json      [ { "codigo": "LIB", "nombre": "Liberia", "ubicacion": "Liberia", "puerto": 7001 }, ... ]
 *                 El orden define el índice de cada sede en NodoCatalogo.existencias (0, 1, 2, 3).
 *                 "ubicacion" debe ser el mismo nombre del lugar que aparece en rutas.json.
 *
 * productos.json  [ { "codigo": "P01", "nombre": "Agua", "descripcion": "Botella 1 L", "categoria": "Bebidas",
 *                     "precio": 500, "existencias": [10, 0, 25, 5] }, ... ]
 *                 "existencias" es el inventario inicial de cada sede, en el orden de sedes.json
 *                 (si se omite, empieza en 0 en todas).
 *
 * app.json        { "rabbitHost": "192.168.0.10", "rabbitPuerto": 5672, "rabbitUsuario": "convitec",
 *                   "rabbitClave": "...", "carpetaDatos": "data" }
 *
 * rutas.json      lo lee el grafo (P2); aquí solo se da su ruta con getRutaRutas().
 * </pre>
 */
public class Config {

    /** Valores de app.json. Gson llena los campos por nombre. */
    private static class AppJson {
        String rabbitHost = "localhost";
        int rabbitPuerto = 5672;
        String rabbitUsuario;
        String rabbitClave;
        String carpetaDatos = "data";
    }

    /** Una entrada de productos.json (producto plano + existencias iniciales). */
    private static class ProductoJson {
        String codigo;
        String nombre;
        String descripcion;
        String categoria;
        double precio;
        int[] existencias;
    }

    private final Path carpetaConfig;
    private final Sede[] sedes;
    private final int indiceSedeActual;
    private final NodoCatalogo[] productosIniciales;
    private final AppJson app;

    private Config(Path carpetaConfig, Sede[] sedes, int indiceSedeActual,
                   NodoCatalogo[] productosIniciales, AppJson app) {
        this.carpetaConfig = carpetaConfig;
        this.sedes = sedes;
        this.indiceSedeActual = indiceSedeActual;
        this.productosIniciales = productosIniciales;
        this.app = app;
    }

    /**
     * Lee sedes.json, productos.json y app.json de la carpeta indicada.
     *
     * @param carpetaConfig normalmente "config"
     * @param codigoSede    la sede que se está arrancando, por ejemplo "ALA"
     */
    public static Config cargar(String carpetaConfig, String codigoSede) {
        Path carpeta = Path.of(carpetaConfig);
        Gson gson = new Gson();

        Sede[] sedes = leer(gson, carpeta.resolve("sedes.json"), Sede[].class);
        validarSedes(sedes);
        int indice = buscarIndice(sedes, codigoSede);
        if (indice < 0) {
            throw new IllegalArgumentException("La sede '" + codigoSede + "' no está en sedes.json. Sedes válidas: "
                    + codigosDe(sedes));
        }

        ProductoJson[] productos = leer(gson, carpeta.resolve("productos.json"), ProductoJson[].class);
        NodoCatalogo[] catalogo = convertirProductos(productos, sedes.length);

        AppJson app = leer(gson, carpeta.resolve("app.json"), AppJson.class);

        return new Config(carpeta, sedes, indice, catalogo, app);
    }

    // ---------- Consultas ----------

    /** Las sedes en el orden de sedes.json. */
    public Sede[] getSedes() {
        Sede[] copia = new Sede[sedes.length];
        for (int i = 0; i < sedes.length; i++) {
            copia[i] = sedes[i];
        }
        return copia;
    }

    public int getCantidadSedes() {
        return sedes.length;
    }

    public Sede getSedeActual() {
        return sedes[indiceSedeActual];
    }

    /** Índice de la sede actual en NodoCatalogo.existencias. */
    public int getIndiceSedeActual() {
        return indiceSedeActual;
    }

    /** Índice de una sede por su código, o -1 si no existe. */
    public int indiceDe(String codigoSede) {
        return buscarIndice(sedes, codigoSede);
    }

    /**
     * Catálogo inicial (productos con sus existencias de arranque), para el primer arranque
     * de la sede cuando todavía no hay data/SEDE/inventario.json. Cada llamada devuelve
     * nodos nuevos, así modificarlos no cambia la configuración.
     */
    public NodoCatalogo[] getProductosIniciales() {
        NodoCatalogo[] copia = new NodoCatalogo[productosIniciales.length];
        for (int i = 0; i < productosIniciales.length; i++) {
            NodoCatalogo original = productosIniciales[i];
            NodoCatalogo nuevo = new NodoCatalogo(original.getProducto(), original.getCantidadSedes());
            for (int s = 0; s < original.getCantidadSedes(); s++) {
                nuevo.setExistencia(s, original.getExistencia(s));
            }
            copia[i] = nuevo;
        }
        return copia;
    }

    public String getRabbitHost() { return app.rabbitHost; }
    public int getRabbitPuerto() { return app.rabbitPuerto; }
    public String getRabbitUsuario() { return app.rabbitUsuario; }
    public String getRabbitClave() { return app.rabbitClave; }

    /** Carpeta de datos de la sede actual, por ejemplo "data/ALA". Se usa con AlmacenJson. */
    public String getCarpetaDatosSede() {
        return Path.of(app.carpetaDatos, getSedeActual().getCodigo()).toString();
    }

    /** Ruta de rutas.json, para que el grafo lo cargue. */
    public String getRutaRutas() {
        return carpetaConfig.resolve("rutas.json").toString();
    }

    // ---------- Lectura y validación ----------

    private static <T> T leer(Gson gson, Path archivo, Class<T> tipo) {
        if (!Files.exists(archivo)) {
            throw new IllegalStateException("Falta el archivo de configuración " + archivo);
        }
        try {
            T datos = gson.fromJson(Files.readString(archivo, StandardCharsets.UTF_8), tipo);
            if (datos == null) {
                throw new IllegalStateException("El archivo de configuración " + archivo + " está vacío");
            }
            return datos;
        } catch (JsonParseException e) {
            throw new IllegalStateException("El archivo de configuración " + archivo + " no es JSON válido: "
                    + e.getMessage(), e);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + archivo, e);
        }
    }

    private static void validarSedes(Sede[] sedes) {
        if (sedes.length == 0) {
            throw new IllegalStateException("sedes.json no tiene sedes");
        }
        for (int i = 0; i < sedes.length; i++) {
            Sede sede = sedes[i];
            if (sede == null || sede.getCodigo() == null || sede.getUbicacion() == null) {
                throw new IllegalStateException("En sedes.json la sede #" + (i + 1) + " no tiene codigo o ubicacion");
            }
            for (int j = 0; j < i; j++) {
                if (sedes[j].getCodigo().equals(sede.getCodigo())) {
                    throw new IllegalStateException("En sedes.json el código " + sede.getCodigo() + " está repetido");
                }
            }
        }
    }

    private static NodoCatalogo[] convertirProductos(ProductoJson[] productos, int cantidadSedes) {
        NodoCatalogo[] catalogo = new NodoCatalogo[productos.length];
        for (int i = 0; i < productos.length; i++) {
            ProductoJson p = productos[i];
            if (p == null || p.codigo == null || p.nombre == null) {
                throw new IllegalStateException("En productos.json el producto #" + (i + 1) + " no tiene codigo o nombre");
            }
            if (p.precio < 0) {
                throw new IllegalStateException("En productos.json el producto " + p.codigo + " tiene precio negativo");
            }
            for (int j = 0; j < i; j++) {
                if (catalogo[j].getCodigo().equals(p.codigo)) {
                    throw new IllegalStateException("En productos.json el código " + p.codigo + " está repetido");
                }
            }
            NodoCatalogo nodo = new NodoCatalogo(
                    new Producto(p.codigo, p.nombre, p.descripcion, p.categoria, p.precio), cantidadSedes);
            if (p.existencias != null) {
                if (p.existencias.length != cantidadSedes) {
                    throw new IllegalStateException("En productos.json el producto " + p.codigo + " tiene "
                            + p.existencias.length + " existencias y hay " + cantidadSedes + " sedes");
                }
                for (int s = 0; s < cantidadSedes; s++) {
                    if (p.existencias[s] < 0) {
                        throw new IllegalStateException("En productos.json el producto " + p.codigo
                                + " tiene una existencia negativa");
                    }
                    nodo.setExistencia(s, p.existencias[s]);
                }
            }
            catalogo[i] = nodo;
        }
        return catalogo;
    }

    private static int buscarIndice(Sede[] sedes, String codigo) {
        if (codigo == null) {
            return -1;
        }
        for (int i = 0; i < sedes.length; i++) {
            if (sedes[i].getCodigo().equals(codigo)) {
                return i;
            }
        }
        return -1;
    }

    private static String codigosDe(Sede[] sedes) {
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < sedes.length; i++) {
            if (i > 0) {
                texto.append(", ");
            }
            texto.append(sedes[i].getCodigo());
        }
        return texto.toString();
    }
}
