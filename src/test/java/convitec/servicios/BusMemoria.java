package convitec.servicios;

import com.google.gson.Gson;
import convitec.estructuras.ListaEnlazada;
import convitec.mensajeria.CanalMensajes;
import convitec.mensajeria.ClienteRabbit;
import convitec.mensajeria.ManejadorMensajes;
import convitec.modelo.Mensaje;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Imita el exchange fanout de RabbitMQ en memoria, solo para pruebas: cada mensaje se pasa a
 * JSON y se entrega a la "cola" de todas las sedes conectadas. Cada sede procesa su cola en su
 * propio hilo, en orden, igual que con el consumidor real. Una sede apagada no recibe nada.
 */
class BusMemoria {
    private final ListaEnlazada<Extremo> extremos = new ListaEnlazada<>();
    private final Gson gson = new Gson();

    synchronized Extremo conectar(String sede) {
        Extremo extremo = new Extremo(sede);
        extremos.agregar(extremo);
        return extremo;
    }

    /** Espera a que todas las sedes terminen de procesar lo que tienen en cola (y lo que eso genere). */
    void sincronizar() throws Exception {
        for (int vuelta = 0; vuelta < 5; vuelta++) {
            for (Extremo e : todos()) {
                e.hilo.submit(() -> { }).get();
            }
        }
    }

    void cerrar() {
        for (Extremo e : todos()) {
            e.hilo.shutdownNow();
        }
    }

    private synchronized Extremo[] todos() {
        return extremos.toArray(new Extremo[0]);
    }

    class Extremo implements CanalMensajes {
        final String sede;
        final ExecutorService hilo = Executors.newSingleThreadExecutor();
        private final ListaEnlazada<ManejadorMensajes> manejadores = new ListaEnlazada<>();
        volatile boolean apagado;

        Extremo(String sede) {
            this.sede = sede;
        }

        @Override
        public void enviar(Mensaje mensaje) {
            byte[] cuerpo = gson.toJson(mensaje).getBytes(StandardCharsets.UTF_8);
            for (Extremo destino : todos()) {
                destino.entregar(cuerpo);
            }
        }

        void entregar(byte[] cuerpo) {
            if (apagado) {
                return;
            }
            hilo.submit(() -> {
                Mensaje m = ClienteRabbit.interpretar(cuerpo, sede, gson);
                if (m == null || apagado) {
                    return;
                }
                ManejadorMensajes[] copia;
                synchronized (this) {
                    copia = manejadores.toArray(new ManejadorMensajes[0]);
                }
                for (ManejadorMensajes h : copia) {
                    h.manejar(m);
                }
            });
        }

        @Override
        public synchronized void alRecibir(ManejadorMensajes manejador) {
            manejadores.agregar(manejador);
        }
    }
}
