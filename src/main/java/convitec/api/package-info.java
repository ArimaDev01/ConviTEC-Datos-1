/**
 * API REST con Javalin. Responsable: P4.
 *
 * Importante: el proyecto usa Gson, no Jackson. Al crear el servidor hay que decirle
 * a Javalin que use Gson; si no, cada ctx.json(...) responde error 500:
 * <pre>
 *   Javalin app = Javalin.create(c -> c.jsonMapper(new io.javalin.json.JavalinGson()));
 * </pre>
 */
package convitec.api;
