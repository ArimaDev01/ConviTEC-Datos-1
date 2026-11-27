package convitec.api;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import convitec.modelo.LineaPedido;
import convitec.modelo.PasoRecorrido;
import convitec.modelo.Recorrido;
import convitec.servicios.EstadisticasService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JavalinGson;

/**
 * Servidor web de una sede: sirve la página (src/main/resources/public) y la API REST.
 *
 * Rutas (todas responden JSON):
 * <pre>
 *  1. GET  /api/sede                  datos de la sede, las sedes y los destinos posibles
 *  2. GET  /api/catalogo              catálogo global con la existencia en cada sede
 *  3. GET  /api/inventario            inventario local (precio y existencia de esta sede)
 *  4. POST /api/inventario/{codigo}   agregar unidades: { "cantidad": 10 }
 *  5. GET  /api/pedidos               historial de pedidos (todos los estados)
 *  6. GET  /api/pedidos/pendientes    pedidos que esperan entrega
 *  7. POST /api/pedidos               nuevo pedido: { "destino", "mostrador", "items": [{ "codigo", "cantidad" }] }
 *  8. GET  /api/entregas              recorridos de entrega con sus pasos y tiempo total
 *  9. POST /api/entregas/siguiente    hace la siguiente entrega (Dijkstra desde la ubicación actual)
 * 10. GET  /api/ventas                historial de ventas
 * 11. GET  /api/estadisticas          producto estrella, más vendidos y peores pedidos
 * </pre>
 * Una entrada inválida responde HTTP 400 con { "error": "..." }. Un pedido que no se puede
 * completar no es un error de la solicitud: responde 200 con ok = false y el motivo.
 */
public class ServidorRest {

    private final DatosSede datos;
    private final EstadisticasService estadisticas;
    private final Gson gson = new Gson();
    private Javalin app;

    public ServidorRest(DatosSede datos, EstadisticasService estadisticas) {
        if (datos == null || estadisticas == null) {
            throw new IllegalArgumentException("ServidorRest necesita los datos de la sede y las estadísticas");
        }
        this.datos = datos;
        this.estadisticas = estadisticas;
    }

    /** Arranca el servidor en el puerto de la sede (por ejemplo 7002 para ALA). */
    public void iniciar(int puerto) {
        if (app != null) {
            throw new IllegalStateException("El servidor ya está iniciado");
        }
        app = Javalin.create(config -> {
            // El proyecto usa Gson: sin esto, ctx.json(...) responde error 500.
            config.jsonMapper(new JavalinGson());
            config.staticFiles.add("/public", Location.CLASSPATH);
        });
        registrarRutas(app);
        registrarErrores(app);
        app.start(puerto);
    }

    /** Detiene el servidor (lo usan las pruebas y el cierre de la aplicación). */
    public void detener() {
        if (app != null) {
            app.stop();
            app = null;
        }
    }

    private void registrarRutas(Javalin app) {
        app.get("/api/sede", ctx -> ctx.json(datos.info()));
        app.get("/api/catalogo", ctx -> ctx.json(datos.catalogo()));
        app.get("/api/inventario", this::inventarioLocal);
        app.post("/api/inventario/{codigo}", this::agregarUnidades);
        app.get("/api/pedidos", ctx -> ctx.json(datos.pedidos()));
        app.get("/api/pedidos/pendientes", ctx -> ctx.json(datos.pendientes()));
        app.post("/api/pedidos", this::registrarPedido);
        app.get("/api/entregas", ctx -> ctx.json(datos.recorridos()));
        app.post("/api/entregas/siguiente", this::siguienteEntrega);
        app.get("/api/ventas", ctx -> ctx.json(datos.ventas()));
        app.get("/api/estadisticas",
                ctx -> ctx.json(new Respuestas.Estadisticas(estadisticas, datos.ventas().length)));
    }

    private void registrarErrores(Javalin app) {
        app.exception(IllegalArgumentException.class,
                (e, ctx) -> ctx.status(400).json(new Respuestas.Error(e.getMessage())));
        app.exception(JsonParseException.class,
                (e, ctx) -> ctx.status(400).json(new Respuestas.Error("El cuerpo de la solicitud no es JSON válido")));
        app.exception(IllegalStateException.class,
                (e, ctx) -> ctx.status(409).json(new Respuestas.Error(e.getMessage())));
        app.exception(Exception.class, (e, ctx) -> {
            e.printStackTrace();
            ctx.status(500).json(new Respuestas.Error("Error interno: " + e.getMessage()));
        });
        app.error(404, ctx -> {
            if (ctx.path().startsWith("/api/")) {
                ctx.json(new Respuestas.Error("Ruta inexistente: " + ctx.path()));
            }
        });
    }

    // ---------- Rutas con lógica propia ----------

    private void inventarioLocal(Context ctx) {
        int indice = datos.info().getIndice();
        var catalogo = datos.catalogo();
        Respuestas.ItemInventario[] items = new Respuestas.ItemInventario[catalogo.length];
        for (int i = 0; i < catalogo.length; i++) {
            items[i] = new Respuestas.ItemInventario(catalogo[i], indice);
        }
        ctx.json(items);
    }

    private void agregarUnidades(Context ctx) {
        String codigo = ctx.pathParam("codigo");
        Respuestas.SolicitudUnidades cuerpo = leerCuerpo(ctx, Respuestas.SolicitudUnidades.class);
        if (cuerpo.cantidad == null) {
            throw new IllegalArgumentException("Falta la cantidad");
        }
        if (cuerpo.cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que 0");
        }
        datos.agregarUnidades(codigo, cuerpo.cantidad);
        ctx.json(new Respuestas.Ok("Se agregaron " + cuerpo.cantidad + " unidades de " + codigo));
    }

    private void registrarPedido(Context ctx) {
        Respuestas.SolicitudPedido cuerpo = leerCuerpo(ctx, Respuestas.SolicitudPedido.class);
        LineaPedido[] items = validarItems(cuerpo.items);
        String destino = cuerpo.destino == null ? "" : cuerpo.destino.trim();
        if (!cuerpo.mostrador && destino.isEmpty()) {
            throw new IllegalArgumentException("Indique el punto de entrega o marque venta de mostrador");
        }
        ctx.json(datos.registrarPedido(destino.isEmpty() ? null : destino, cuerpo.mostrador, items));
    }

    private void siguienteEntrega(Context ctx) {
        PasoRecorrido paso = datos.siguientePaso();
        Recorrido[] recorridos = datos.recorridos();
        Recorrido actual = recorridos.length == 0 ? null : recorridos[recorridos.length - 1];
        String mensaje = paso == null
                ? "No hay pedidos pendientes para entregar"
                : "Entregado en " + paso.getHasta() + " (" + paso.getMinutos() + " min)";
        ctx.json(new SiguienteEntrega(paso, actual, mensaje));
    }

    /** Respuesta de POST /api/entregas/siguiente. */
    private static class SiguienteEntrega {
        final PasoRecorrido paso;
        final Recorrido recorrido;
        final String mensaje;

        SiguienteEntrega(PasoRecorrido paso, Recorrido recorrido, String mensaje) {
            this.paso = paso;
            this.recorrido = recorrido;
            this.mensaje = mensaje;
        }
    }

    // ---------- Validación de entradas ----------

    private <T> T leerCuerpo(Context ctx, Class<T> tipo) {
        String texto = ctx.body();
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("La solicitud no trae cuerpo JSON");
        }
        T cuerpo = gson.fromJson(texto, tipo);
        if (cuerpo == null) {
            throw new IllegalArgumentException("La solicitud no trae cuerpo JSON");
        }
        return cuerpo;
    }

    /** Revisa cada línea y la convierte a LineaPedido. Las líneas repetidas las suma el servicio de pedidos. */
    static LineaPedido[] validarItems(Respuestas.SolicitudPedido.Linea[] items) {
        if (items == null || items.length == 0) {
            throw new IllegalArgumentException("El pedido debe tener al menos un producto");
        }
        LineaPedido[] lineas = new LineaPedido[items.length];
        for (int i = 0; i < items.length; i++) {
            Respuestas.SolicitudPedido.Linea item = items[i];
            if (item == null || item.codigo == null || item.codigo.isBlank()) {
                throw new IllegalArgumentException("La línea " + (i + 1) + " no indica el producto");
            }
            if (item.cantidad == null || item.cantidad <= 0) {
                throw new IllegalArgumentException("La cantidad de " + item.codigo + " debe ser mayor que 0");
            }
            lineas[i] = new LineaPedido(item.codigo.trim(), item.cantidad);
        }
        return lineas;
    }
}
