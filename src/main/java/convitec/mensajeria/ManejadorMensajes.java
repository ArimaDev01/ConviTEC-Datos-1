package convitec.mensajeria;

import convitec.modelo.Mensaje;

/** Lo que hace una sede cuando le llega un mensaje que sí es para ella. */
@FunctionalInterface
public interface ManejadorMensajes {
    void manejar(Mensaje mensaje);
}
