package convitec;

import convitec.api.DatosFalsos;
import convitec.api.DatosSede;
import convitec.api.ServidorRest;
import convitec.grafo.Grafo;
import convitec.persistencia.Config;
import convitec.servicios.EstadisticasService;

/**
 * Punto de entrada: arranca UNA sede.
 *
 * <pre>
 *   java -jar target/convitec.jar ALA             usa la carpeta config/
 *   java -jar target/convitec.jar ALA otraConfig  usa otra carpeta de configuración
 *   java -jar target/convitec.jar ALA --demo      datos falsos en memoria, sin archivos ni RabbitMQ
 * </pre>
 *
 * Orden de arranque: configuración → grafo → servicios de la sede → estadísticas → servidor web.
 * Mientras P2 y P3 terminan DespachoService, InventarioService, PedidoService, ClienteRabbit y
 * RelojVirtual, la sede usa DatosFalsos; cuando existan, se crean aquí y reemplazan esa línea.
 */
public final class App {

    private static final String USO = "Uso: java -jar convitec.jar <SEDE> [carpetaConfig | --demo]   (SEDE: LIB, ALA, SJO, LIM)";

    private App() {
    }

    public static void main(String[] args) {
        if (args.length < 1 || args[0].isBlank()) {
            System.err.println(USO);
            System.exit(1);
        }
        String codigo = args[0].trim().toUpperCase();
        boolean demo = args.length > 1 && args[1].equals("--demo");
        String carpetaConfig = args.length > 1 && !demo ? args[1] : "config";

        try {
            if (demo) {
                arrancarDemo(codigo);
            } else {
                arrancar(codigo, carpetaConfig);
            }
        } catch (RuntimeException e) {
            System.err.println("No se pudo arrancar la sede " + codigo + ": " + e.getMessage());
            System.err.println("Para probar solo la página: java -jar convitec.jar " + codigo + " --demo");
            System.exit(1);
        }
    }

    private static void arrancar(String codigo, String carpetaConfig) {
        Config config = Config.cargar(carpetaConfig, codigo);
        Grafo grafo = Grafo.cargar(config.getRutaRutas());

        // TODO(P2/P3): reemplazar por los servicios reales cuando estén en development:
        //   ClienteRabbit rabbit = new ClienteRabbit(config);
        //   InventarioService inventario = ...; PedidoService pedidos = ...; DespachoService despacho = ...;
        //   new RelojVirtual(despacho).iniciar();
        DatosSede datos = new DatosFalsos(config.getSedes(), codigo, grafo.lugares(), config.getProductosIniciales());

        iniciarServidor(datos, config.getSedeActual().getPuerto());
    }

    private static void arrancarDemo(String codigo) {
        DatosFalsos datos = new DatosFalsos(codigo);
        iniciarServidor(datos, puertoDemo(datos));
    }

    private static void iniciarServidor(DatosSede datos, int puerto) {
        EstadisticasService estadisticas = new EstadisticasService(datos::ventas);
        ServidorRest servidor = new ServidorRest(datos, estadisticas);
        servidor.iniciar(puerto);
        Runtime.getRuntime().addShutdownHook(new Thread(servidor::detener));
        System.out.println("Sede " + datos.info().getCodigo() + " (" + datos.info().getNombre()
                + ") lista en http://localhost:" + puerto + (datos.info().isDatosFalsos() ? "  [datos falsos]" : ""));
    }

    private static int puertoDemo(DatosSede datos) {
        var info = datos.info();
        return info.getSedes()[info.getIndice()].getPuerto();
    }
}
