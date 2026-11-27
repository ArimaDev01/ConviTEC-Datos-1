package convitec.servicios;

import convitec.estructuras.ListaEnlazada;
import convitec.modelo.Mensaje;

/**
 * Las reservas que un pedido le pidió a otras sedes y que todavía esperan RESPUESTA.
 * El hilo que registra el pedido se queda en {@link #esperar(long)} y el hilo de RabbitMQ
 * lo despierta con {@link #recibir(Mensaje)} cada vez que llega una respuesta.
 */
class EsperaReservas {
    /** Una línea pedida a una sede. */
    private static class Solicitud {
        final String sede;
        final String codigo;
        boolean respondida;

        Solicitud(String sede, String codigo) {
            this.sede = sede;
            this.codigo = codigo;
        }
    }

    private final String pedidoId;
    private final ListaEnlazada<Solicitud> solicitudes = new ListaEnlazada<>();
    private int sinResponder;
    private String rechazo;

    EsperaReservas(String pedidoId) {
        this.pedidoId = pedidoId;
    }

    String getPedidoId() {
        return pedidoId;
    }

    /** Anota que se le pidió el producto a esa sede (antes de enviar el RESERVAR). */
    synchronized void agregar(String sede, String codigo) {
        solicitudes.agregar(new Solicitud(sede, codigo));
        sinResponder++;
    }

    /** Llegó una RESPUESTA para este pedido. Las repetidas o desconocidas se ignoran. */
    synchronized void recibir(Mensaje respuesta) {
        for (Solicitud s : solicitudes.toArray(new Solicitud[0])) {
            if (!s.respondida && s.sede.equals(respuesta.getDe()) && s.codigo.equals(respuesta.getCodigo())) {
                s.respondida = true;
                sinResponder--;
                if (!Boolean.TRUE.equals(respuesta.getOk()) && rechazo == null) {
                    rechazo = respuesta.getMotivo() != null ? respuesta.getMotivo()
                            : "La sede " + s.sede + " rechazó la reserva de " + s.codigo;
                }
                notifyAll();
                return;
            }
        }
    }

    /**
     * Espera hasta que respondan todas, alguna rechace o pase el tiempo.
     * @return true si todas respondieron que sí
     */
    synchronized boolean esperar(long milisegundos) throws InterruptedException {
        long limite = System.currentTimeMillis() + milisegundos;
        while (sinResponder > 0 && rechazo == null) {
            long falta = limite - System.currentTimeMillis();
            if (falta <= 0) {
                break;
            }
            wait(falta);
        }
        return sinResponder == 0 && rechazo == null;
    }

    synchronized String getRechazo() {
        return rechazo;
    }

    /** Códigos de las sedes que no han respondido, sin repetir, separados por coma. */
    synchronized String sedesSinRespuesta() {
        StringBuilder texto = new StringBuilder();
        for (Solicitud s : solicitudes.toArray(new Solicitud[0])) {
            if (!s.respondida && texto.indexOf(s.sede) < 0) {
                if (!texto.isEmpty()) {
                    texto.append(", ");
                }
                texto.append(s.sede);
            }
        }
        return texto.toString();
    }
}
