package convitec.servicios;

import convitec.modelo.NodoCatalogo;
import convitec.modelo.Producto;
import convitec.modelo.Sede;

/** Sedes y productos de prueba (mismo orden que config/sedes.json: LIB 0, ALA 1, SJO 2, LIM 3). */
final class Datos {
    static final int LIB = 0;
    static final int ALA = 1;
    static final int SJO = 2;
    static final int LIM = 3;

    private Datos() {
    }

    static Sede[] sedes() {
        return new Sede[]{
                new Sede("LIB", "Liberia", "Liberia", 7001),
                new Sede("ALA", "Alajuela", "Alajuela", 7002),
                new Sede("SJO", "San José", "San José", 7003),
                new Sede("LIM", "Limón", "Limón", 7004)
        };
    }

    /** P01 Agua [20,15,30,10], P02 Café [5,12,0,8], P03 Galletas [10,0,25,4]. */
    static NodoCatalogo[] productos() {
        return new NodoCatalogo[]{
                nodo("P01", "Agua 600 ml", 650, 20, 15, 30, 10),
                nodo("P02", "Café molido 250 g", 2450, 5, 12, 0, 8),
                nodo("P03", "Galletas de soda", 900, 10, 0, 25, 4)
        };
    }

    private static NodoCatalogo nodo(String codigo, String nombre, double precio, int... existencias) {
        NodoCatalogo nodo = new NodoCatalogo(new Producto(codigo, nombre, nombre, "Prueba", precio), existencias.length);
        for (int i = 0; i < existencias.length; i++) {
            nodo.setExistencia(i, existencias[i]);
        }
        return nodo;
    }
}
