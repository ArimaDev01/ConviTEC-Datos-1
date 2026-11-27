# ConviTEC — Inventario distribuido y sistema de pedidos

Proyecto 2 del curso CE1103 Algoritmos y Estructuras de Datos I — Instituto Tecnológico de Costa Rica.

ConviTEC simula una cadena de tiendas de conveniencia con cuatro sedes (Liberia, Alajuela, San José y Limón).
Cada sede es una instancia independiente de la aplicación: tiene su propio servidor web, su inventario y sus
pedidos en archivos locales, y se comunica con las demás únicamente por mensajes de RabbitMQ.

## Requisitos

| Herramienta | Versión | Para qué |
|---|---|---|
| JDK | 26 | compilar y ejecutar |
| Maven | 3.9 o superior | dependencias, pruebas y el `.jar` |
| RabbitMQ | 3.13 o superior | mensajes entre sedes (solo en una de las computadoras) |
| Navegador | cualquiera reciente | la página de cada sede |

Dependencias (las baja Maven): Javalin (servidor web y API REST), Gson (JSON), amqp-client (RabbitMQ) y JUnit 5.
Ninguna estructura de datos del proyecto usa colecciones del lenguaje: listas, colas, árbol AVL, grafo y
ordenamiento están implementados en `convitec.estructuras` y `convitec.grafo`.

## Compilar y probar

```bash
mvn test          # corre todas las pruebas
mvn package       # genera target/convitec.jar con todas las dependencias
```

Antes de cada Pull Request este comando debe salir vacío (detecta colecciones nativas y `sort()`, prohibidos):

```bash
grep -rn "ArrayList\|HashMap\|LinkedList\|List<\|Map<\|Set<\|\.sort(\|stream()" src/main
```

## Configuración

Todo está en la carpeta `config/`:

| Archivo | Contenido |
|---|---|
| `sedes.json` | código, nombre, ubicación en el grafo y puerto web de cada sede (LIB 7001, ALA 7002, SJO 7003, LIM 7004) |
| `rutas.json` | rutas de distribución: origen, destino, código de ruta y tiempo en minutos |
| `productos.json` | catálogo inicial con la existencia de cada producto en cada sede |
| `app.json` | host, puerto, usuario y clave de RabbitMQ, y la carpeta de datos (`data/` por defecto) |

Cada sede guarda sus archivos en `data/<CODIGO>/` (por ejemplo `data/ALA/`) y los vuelve a cargar al arrancar.

## Ejecutar

Una sede por terminal, indicando su código:

```bash
java -jar target/convitec.jar ALA
```

Luego se abre `http://localhost:7002` (el puerto de la sede en `sedes.json`).

Para ver solo la página con datos de ejemplo en memoria, sin archivos de configuración ni RabbitMQ:

```bash
java -jar target/convitec.jar ALA --demo
```

### Las 4 sedes en 2 computadoras

1. Instalar RabbitMQ en la computadora A y crear el usuario `convitec` (el usuario `guest` solo funciona en `localhost`).
   *Pasos detallados: pendiente (P3).*
2. En las dos computadoras, poner en `config/app.json` la IP de la computadora A como `rabbitHost`.
3. Computadora A: `java -jar target/convitec.jar LIB` y `java -jar target/convitec.jar ALA`.
4. Computadora B: `java -jar target/convitec.jar SJO` y `java -jar target/convitec.jar LIM`.
5. Abrir la página de cada sede en `http://<ip-de-la-computadora>:<puerto>`.

## API REST

Cada sede expone estas rutas en su puerto. Las respuestas son JSON; una entrada inválida responde
HTTP 400 con `{ "error": "..." }`.

| # | Método | Ruta | Descripción |
|---|---|---|---|
| 1 | GET | `/api/sede` | datos de la sede, las cuatro sedes y los destinos posibles |
| 2 | GET | `/api/catalogo` | catálogo global con la existencia en cada sede |
| 3 | GET | `/api/inventario` | inventario local (precio y existencia de esta sede) |
| 4 | POST | `/api/inventario/{codigo}` | agregar unidades: `{ "cantidad": 10 }` |
| 5 | GET | `/api/pedidos` | historial de pedidos (todos los estados) |
| 6 | GET | `/api/pedidos/pendientes` | pedidos que esperan entrega |
| 7 | POST | `/api/pedidos` | nuevo pedido: `{ "destino": "Heredia", "mostrador": false, "items": [{ "codigo": "P01", "cantidad": 2 }] }` |
| 8 | GET | `/api/entregas` | recorridos con sus pasos y tiempo total |
| 9 | POST | `/api/entregas/siguiente` | entrega en el siguiente destino más cercano (Dijkstra) |
| 10 | GET | `/api/ventas` | historial de ventas |
| 11 | GET | `/api/estadisticas` | producto estrella, más vendidos y peores pedidos |

Un pedido que no se puede completar (sin existencias o destino inalcanzable) responde 200 con
`ok: false` y el motivo, porque la solicitud sí era válida.

## Estructura del código

| Paquete | Contenido | Responsable |
|---|---|---|
| `convitec.modelo` | entidades: Producto, Sede, Pedido, Venta, Reserva, Mensaje, Recorrido… | P1 |
| `convitec.estructuras` | ListaEnlazada, Cola, ArbolAVL, Comparador y merge sort propios | P1 |
| `convitec.persistencia` | AlmacenJson (archivos de cada sede) y Config | P1 |
| `convitec.grafo` | grafo con matriz de adyacencia, Dijkstra y planificador | P2 |
| `convitec.mensajeria` | cliente de RabbitMQ | P3 |
| `convitec.servicios` | inventario, pedidos, despacho, reloj y estadísticas | P2, P3 y P4 |
| `convitec.api` | servidor REST y página web (`src/main/resources/public`) | P4 |

## Diagrama UML de clases

*Pendiente (P1 y P4), cuando el código esté estable.*
