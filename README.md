# customer-service

Puerto `8087` · base `customer_db` · paquete `gt.cunoc.sa.customer`. El diseño (enlace por NIT, cartera,
alquileres, eventos) está en el [README del proyecto](../README.md).

Clientes por NIT y su enlace con la cuenta, la cartera digital (un libro de movimientos idempotente), los
alquileres de bus con todo su ciclo de vida, y el historial de boletos y alquileres. Atiende a sales por
REST (S4 débito de cartera y S5 resolver el cliente), publica E13, E14 y E16, y consume E2, E4, E8–E12 y
E15. No llama a ningún otro servicio.

## Librerías

Spring Boot `3.5.16` sobre Java 21. Las versiones que el `pom.xml` no fija las pone el BOM de Boot;
aquí van resueltas (las mismas que empaqueta `target/app.jar`). Son las de identity-service sin Redis ni
Nimbus: el JWT y las revocaciones son solo de identity y el gateway.

| Librería | Versión | Para qué |
|---|---|---|
| Spring Web MVC · Tomcat | 6.2.19 · 10.1.55 | API REST (`/api/**` y `/internal/**`) |
| Spring Data JPA · Hibernate ORM · HikariCP | 3.5.13 · 6.6.53.Final · 6.3.3 | Persistencia en `customer_db`, con `ddl-auto: validate` |
| Hibernate Validator (Bean Validation) | 8.0.3.Final | Validación de los `XRequest` |
| Spring Security | 6.5.11 | Autoriza con los `X-User-*` del gateway (`@PreAuthorize`); no valida el JWT |
| Spring AMQP (`spring-rabbit`) · cliente AMQP | 3.2.12 · 5.25.0 | Publica E13, E14 y E16 por outbox; consume E2, E4, E8–E12 y E15 |
| Flyway (`flyway-core` + `flyway-database-postgresql`) | 11.7.2 | Migraciones de `db/migration` |
| PostgreSQL JDBC | 42.7.11 | Driver de `customer_db` (PostgreSQL 16) |
| Spring Boot Actuator · Micrometer | 3.5.16 · 1.15.12 | `/actuator/health`, el healthcheck de Compose |
| springdoc-openapi (`webmvc-ui`) · Swagger UI | 2.8.17 · 5.32.2 | `/v3/api-docs` y Swagger UI; el gateway los agrega |

| Pruebas | Versión | Para qué |
|---|---|---|
| JUnit Jupiter · Mockito · AssertJ (`spring-boot-starter-test`) | 5.12.2 · 5.17.0 · 3.27.7 | Pruebas unitarias y de integración (`@SpringBootTest`) |
| Testcontainers (`junit-jupiter`, `postgresql`, `rabbitmq`) + `spring-boot-testcontainers` | 1.21.4 · 3.5.16 | PostgreSQL y RabbitMQ reales (`ContenedoresDePrueba`) |
| Awaitility | 4.2.2 | Esperar efectos asíncronos (relay, listener) sin `Thread.sleep` |
| Spring Security Test | 6.5.11 | Declarada, sin uso: las pruebas mandan los `X-User-*` directo (`Escenario.como`) |

Imágenes: `maven:3.9-eclipse-temurin-21` compila y `eclipse-temurin:21-jre` corre (`Dockerfile`);
`postgres:16-alpine` para `customer-db`. Las pruebas usan `postgres:16-alpine` y
`rabbitmq:4-management-alpine`.

## Estructura

```
customer-service/
├── pom.xml                                Boot 3.5.16, finalName app; web, JPA, Flyway, Security, AMQP, springdoc
├── Dockerfile                             dos etapas: Maven compila, JRE 21 corre target/app.jar
├── docker-compose.yml                     proyecto Compose propio: customer-db (red privada) + el servicio (también en transporte)
├── .dockerignore                          deja target/, .env y archivos del IDE fuera de la imagen
├── .gitignore                             lo que no se versiona: target/, .env (no .env.example), logs, IDE y SO
├── .env.example                           variables de su compose (su base y RabbitMQ); se copia a .env, que no se versiona
├── README.md                              estructura y endpoints (este archivo)
├── API.md                                 cada endpoint en detalle (entrada, salida, errores, efectos) y el contrato de los eventos
└── src/
    ├── main/
    │   ├── java/gt/cunoc/sa/customer/
    │   │   ├── CustomerApplication.java   arranque; @EnableScheduling (relay de la outbox)
    │   │   ├── api/                       un paquete por recurso: controller, service (transacción y @PreAuthorize), repository y dto/
    │   │   │   ├── cartera/
    │   │   │   │   ├── CarteraController.java          /api/cartera: saldo, movimientos y recargas
    │   │   │   │   ├── CarteraService.java             cartera propia (X-User-Id); recarga idempotente por Idempotency-Key
    │   │   │   │   ├── CarteraRepository.java          crear, debitar (saldo >= monto) y acreditar con UPDATE … RETURNING
    │   │   │   │   ├── MovimientoCarteraRepository.java libro: por clave, filtros, y la lápida DEBITO_ANULADO (ON CONFLICT)
    │   │   │   │   └── dto/
    │   │   │   │       ├── CarteraResponse.java         {clienteId, saldo, actualizadoEn}
    │   │   │   │       ├── MovimientoResponse.java      un movimiento del libro, con el saldo que dejó
    │   │   │   │       └── RecargaRequest.java          {monto, fecha?}
    │   │   │   ├── alquiler/
    │   │   │   │   ├── AlquilerController.java         /api/alquileres: estimación, solicitud, confirmación, pago, reporte
    │   │   │   │   ├── AlquilerService.java            quién hace qué en el ciclo de vida; precio estimado; E13, E14, E16
    │   │   │   │   ├── AlquilerRepository.java         por cliente, filtros (Specification) y reasignación al absorber
    │   │   │   │   ├── TarifaAlquilerRepository.java   la tarifa vigente a una fecha; inserción idempotente de E2
    │   │   │   │   └── dto/
    │   │   │   │       ├── TrayectoRequest.java          origen y destino con coordenadas (6 decimales)
    │   │   │   │       ├── EstimacionRequest.java        {trayecto, fechaSolicitud?, fechaSalida, fechaRetorno?}
    │   │   │   │       ├── EstimacionResponse.java       distancia, días, tarifas y precio estimado
    │   │   │   │       ├── SolicitudAlquilerRequest.java en línea: + sucursalId y pasajeros
    │   │   │   │       ├── SolicitudMostradorRequest.java en mostrador: + nit, nombre y pasajeros
    │   │   │   │       ├── ConfirmacionRequest.java      {precioFinal?}
    │   │   │   │       ├── RechazoRequest.java           {motivo}
    │   │   │   │       ├── CancelacionRequest.java       {motivo?}
    │   │   │   │       ├── PagoRequest.java              {fechaPago?}
    │   │   │   │       ├── AlquilerResponse.java         el alquiler con su cliente y su asignación
    │   │   │   │       └── ReporteIngresosResponse.java  filas, cantidad y total del reporte de ingresos por alquiler
    │   │   │   ├── historial/
    │   │   │   │   ├── HistorialController.java        /api/historial
    │   │   │   │   ├── HistorialService.java           boletos y alquileres del cliente de la cuenta
    │   │   │   │   ├── CompraBoletoRepository.java     réplica de E11/E12; reasignación al absorber
    │   │   │   │   └── dto/
    │   │   │   │       ├── HistorialResponse.java       {clienteId, nit, nombre, boletos, alquileres}
    │   │   │   │       └── CompraResponse.java          una compra de boletos (CONFIRMADA o ANULADA)
    │   │   │   ├── cliente/
    │   │   │   │   ├── ClienteController.java          /api/clientes: búsqueda por NIT o nombre para el mostrador
    │   │   │   │   ├── ClienteService.java             sin lápidas; los clientes no son de una sucursal
    │   │   │   │   ├── ClienteRepository.java          upserts por NIT (ON CONFLICT), bloqueos y reapuntar lápidas
    │   │   │   │   └── dto/
    │   │   │   │       └── ClienteResponse.java         cliente con o sin cuenta
    │   │   │   ├── interno/                                 /internal/**: solo red interna, el gateway no las expone
    │   │   │   │   ├── InternoController.java               S4, S5 y reprocesar la DLQ
    │   │   │   │   ├── ClientesInternosService.java         S5: cliente por NIT (mostrador) o por cuenta (en línea)
    │   │   │   │   ├── DebitosService.java                  S4: débito idempotente y su anulación (lápida)
    │   │   │   │   └── dto/
    │   │   │   │       ├── ResolucionClienteRequest.java    {nit?, nombre?}
    │   │   │   │       ├── ClienteInternoResponse.java      {clienteId, nit, nombre, correo, generico, conCuenta}
    │   │   │   │       ├── DebitoRequest.java               {monto, fecha?, referenciaId?}
    │   │   │   │       ├── DebitoResponse.java              {resultado, movimientoId, monto, saldo}
    │   │   │   │       ├── AnulacionResponse.java           {resultado}
    │   │   │   │       └── ResultadoDebito.java             APLICADO o ANULADO
    │   │   │   └── error/
    │   │   │       ├── ManejadorErrores.java                todo error → ProblemDetail; restricción única → 409; NIT inválido → 422; transición → 409
    │   │   │       ├── ErrorDeNegocio.java                  error esperado: 400, 401, 404, 409 o 422 con su detalle
    │   │   │       └── ServicioNoDisponibleException.java   otro servicio no respondió → 503
    │   │   ├── domain/                    entidades JPA y reglas
    │   │   │   ├── ClienteEntity.java          cliente por NIT; usuario_id (enlace), genérico y lápida absorbido_por
    │   │   │   ├── CarteraEntity.java          saldo, solo lectura desde JPA (lo cambian los UPDATE condicionales)
    │   │   │   ├── MovimientoCarteraEntity.java fila del libro, inmutable, con su idempotency_key
    │   │   │   ├── AlquilerEntity.java         ciclo de vida: transiciones del usuario (409) y de trip (solo avanzan)
    │   │   │   ├── CompraBoletoEntity.java     réplica de una venta; gana la versión mayor de E11/E12
    │   │   │   ├── TarifaAlquilerEntity.java   réplica de una versión de tarifa (E2)
    │   │   │   ├── Trayecto.java               origen y destino con coordenadas
    │   │   │   ├── Distancia.java              haversine × factor de carretera
    │   │   │   ├── PrecioAlquiler.java         distancia × tarifa × (2 si ida y vuelta) + días × cargo diario
    │   │   │   ├── EstadoAlquiler.java · Canal.java · TipoMovimiento.java · EstadoCompra.java · TipoTarifa.java
    │   │   │   ├── TransicionInvalidaException.java   cambio de estado no permitido → 409
    │   │   │   ├── Rol.java                    SYS_ADMIN, BRANCH_ADMIN, CASHIER, DRIVER, CUSTOMER
    │   │   │   ├── Nit.java                    normalización y dígito verificador módulo 11 (copiado de identity)
    │   │   │   ├── NitInvalidoException.java   → 422
    │   │   │   ├── Calendario.java             "hoy" en America/Guatemala, no en UTC
    │   │   │   └── ConIdAsignado.java          Persistable: persist() y no merge() con UUID asignado
    │   │   ├── application/               casos de uso compartidos entre recursos o sin endpoint; regla de alcance
    │   │   │   ├── ResolucionDeClientes.java    cliente por NIT (S5 y mostrador), de la cuenta, y el vigente tras una absorción
    │   │   │   ├── LibroCartera.java            débito, crédito, lápida y reembolso de lo debitado; claves de idempotencia
    │   │   │   ├── EnlaceDeCuentas.java         E4: alta, enlace por NIT y absorción al cambiar el NIT
    │   │   │   ├── NitDeOtraCuentaException.java E4 con el NIT de otra cuenta: reintentable, luego DLQ
    │   │   │   ├── ReplicaTarifas.java          E2: versiones de ALQUILER_KM y ALQUILER_DIA
    │   │   │   ├── ComprasDeBoletos.java        E11 y E12: réplica del historial y reembolso de la venta anulada
    │   │   │   ├── CicloDeAlquileres.java       E15, E8, E9 y E10: asignación, salida, llegada y cancelación con reembolso
    │   │   │   ├── EventosDeAlquiler.java       E13, E14 y E16 a la outbox
    │   │   │   ├── PropiedadesAlquiler.java     alquiler.factor-carretera
    │   │   │   ├── Alcance.java                 un BRANCH_ADMIN solo toca su sucursal → 403
    │   │   │   └── package-info.java
    │   │   └── infrastructure/
    │   │       ├── messaging/                              outbox, inbox y RabbitMQ (copiado de identity)
    │   │       │   ├── Sobre.java                          sobre de cada evento (eventId, type, aggregateVersion…)
    │   │       │   ├── Outbox.java                         registra el evento en la transacción del cambio
    │   │       │   ├── OutboxEvent.java                    fila de outbox_event
    │   │       │   ├── OutboxRepository.java               pendientes con FOR UPDATE SKIP LOCKED
    │   │       │   ├── RelayOutbox.java                    publica con confirms cada segundo
    │   │       │   ├── Inbox.java                          un evento ya aplicado no tiene efecto
    │   │       │   ├── ProcessedEvent.java                 fila de processed_event
    │   │       │   ├── ProcessedEventRepository.java       INSERT … ON CONFLICT DO NOTHING
    │   │       │   ├── ListenerEventos.java                consume customer.events y llama al manejador de cada tipo
    │   │       │   ├── Payloads.java                       payload → record del evento; ilegible o incompleto → DLQ
    │   │       │   ├── TopologiaRabbit.java                exchanges, cola, DLQ, alternate-exchange y los 8 bindings (E2, E4, E8–E12, E15)
    │   │       │   ├── ReintentosConfig.java               reintentos a 1, 2, 4 y 8 s; lo no reintentable va a la DLQ
    │   │       │   ├── EventoNoProcesableException.java    evento que nunca va a funcionar → DLQ
    │   │       │   ├── ReprocesadorDlq.java                devuelve la DLQ a la cola
    │   │       │   └── eventos/                            payload de cada evento: el contrato con los otros servicios
    │   │       │       ├── TiposEvento.java                routing keys: publica E13, E14, E16; consume E2, E4, E8–E12, E15
    │   │       │       ├── AlquilerConfirmado.java         E13 (publica)
    │   │       │       ├── AlquilerPagado.java             E14 (publica), con el método de pago
    │   │       │       ├── AlquilerCancelado.java          E16 (publica), con a dónde vuelve el dinero
    │   │       │       ├── ConfiguracionActualizada.java   E2 (consume)
    │   │       │       ├── PerfilGuardado.java             E4 (consume)
    │   │       │       ├── ViajeDeAlquiler.java            E8, E9 y E10 (consume): viajeId, tipo y alquilerId
    │   │       │       ├── AlquilerAsignado.java           E15 (consume)
    │   │       │       ├── BoletosVendidos.java            E11 (consume)
    │   │       │       └── BoletosAnulados.java            E12 (consume)
    │   │       ├── security/
    │   │       │   ├── SeguridadConfig.java                cadena de Spring Security; 401 y 403 como ProblemDetail
    │   │       │   ├── EncabezadosGatewayFilter.java       X-User-* del gateway → UsuarioActual
    │   │       │   └── UsuarioActual.java                  principal: usuario, sucursal, chofer y roles
    │   │       └── web/
    │   │           ├── CorrelationIdFilter.java            X-Correlation-Id → MDC y respuesta
    │   │           ├── DocumentacionConfig.java            OpenAPI: servidor relativo y bearerAuth
    │   │           └── WebConfig.java                      páginas como {content, page}
    │   └── resources/
    │       ├── application.yml                 puerto, base, RabbitMQ y el factor de carretera
    │       └── db/migration/                   Flyway
    │           ├── V1__esquema_inicial.sql     cliente (con la fila genérica CF), cartera, movimientos, alquiler,
    │           │                               réplicas de tarifas y compras, outbox e inbox
    │           └── V2__alquiler_asignacion.sql placa, chofer y versión de la asignación (E15); índice del reporte
    └── test/
        ├── java/gt/cunoc/sa/customer/
        │   ├── PruebaDeIntegracion.java        meta-anotación: un solo contexto para todas
        │   ├── ContenedoresDePrueba.java       PostgreSQL y RabbitMQ en Testcontainers
        │   ├── Escenario.java                  usuarios del gateway, clientes, compras, alquileres y tarifas escritos directo en la base
        │   ├── Publicador.java                 publica eventos como identity, trip o sales; inbox y DLQ
        │   ├── ColaDePrueba.java               cola temporal que ve lo que publica el relay
        │   ├── CustomerApplicationTests.java   migraciones aplicadas y cliente genérico
        │   ├── SeguridadTest.java              401, 404, health y X-Correlation-Id
        │   ├── domain/
        │   │   ├── NitTest.java                normalización, dígito verificador, K y CF
        │   │   ├── PrecioAlquilerTest.java     fórmula, días y redondeo
        │   │   ├── DistanciaTest.java          haversine y factor de carretera
        │   │   └── AlquilerEntityTest.java     transiciones del ciclo de vida
        │   └── application/
        │       ├── EnlaceDeCuentasIT.java      E4: alta, enlace, absorción, duplicado, desorden, DLQ y carrera con S5
        │       ├── ReplicaTarifasIT.java       E2: versiones de alquiler una sola vez; depreciación descartada
        │       ├── ResolucionDeClientesIT.java S5 y /api/clientes: genérico, NIT nuevo, concurrencia, 409 y 422
        │       ├── CarteraIT.java              recargas idempotentes, S4, débitos concurrentes y la lápida
        │       ├── AlquileresIT.java           ciclo por endpoints con E13, E14, E16, autorización y reporte
        │       ├── CicloDeAlquileresIT.java    E15, E8, E9 y E10: avance, desorden y reembolso
        │       └── HistorialIT.java            E11 y E12: historial, lápida, desorden y reembolso de lo debitado
        └── resources/config/
            └── application.yml   intervalo corto del relay (se suma al principal)
```

## Endpoints

Resumen de una línea por endpoint. El detalle de cada uno (campos y validaciones, ejemplos, cada error
con su `detail` y los eventos que emite) está en [API.md](API.md).

Las rutas `/api/**` llegan por el gateway, que valida el JWT y agrega `X-User-*`. Todas las respuestas de
error son `ProblemDetail`; sin identidad → `401`, rol, dueño o sucursal ajenos → `403`. "Cuenta propia" es
cualquier usuario autenticado sobre su propio cliente (sale de `X-User-Id`); responde `409` mientras
customer no reciba el `PerfilGuardado` de una cuenta recién confirmada.

### Cartera — `/api/cartera`

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| GET | `/api/cartera` | cuenta propia | Saldo |
| GET | `/api/cartera/movimientos` | cuenta propia | Paginado, del más reciente; filtros `desde` y `hasta` sobre la fecha elegida. Sin las lápidas del reconciliador |
| POST | `/api/cartera/recargas` | cuenta propia | Encabezado `Idempotency-Key` y `{monto, fecha?}` → `201`; la misma clave → `200` con la recarga original (no acredita dos veces). 10 por minuto |

### Historial — `/api/historial`

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| GET | `/api/historial` | cuenta propia | `{boletos, alquileres}` en línea y en mostrador, incluidos los previos con su NIT; `desde` y `hasta` opcionales |

### Alquileres — `/api/alquileres`

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| POST | `/api/alquileres/estimacion` | autenticado | Distancia, días, tarifas y precio estimado, sin guardar. `422` fechas, mismo lugar o sin tarifas |
| POST | `/api/alquileres` | cuenta propia | Solicitud en línea con `sucursalId` → `201` `SOLICITADO` con su precio estimado |
| POST | `/api/alquileres/mostrador` | `BRANCH_ADMIN`, `CASHIER` | Solicitud presencial; cliente por NIT (sin NIT, `CF`), sucursal del token → `201` |
| GET | `/api/alquileres/mios` | cuenta propia | Los de su cliente, paginados |
| GET | `/api/alquileres` | `SYS_ADMIN`; `BRANCH_ADMIN` y `CASHIER` (su sucursal) | Paginado; filtros `estado` y `sucursalId` (solo `SYS_ADMIN`) |
| GET | `/api/alquileres/{id}` | su cliente, el personal de su sucursal, `SYS_ADMIN` | Detalle |
| POST | `/api/alquileres/{id}/confirmacion` | `BRANCH_ADMIN` de la sucursal, `SYS_ADMIN` | `{precioFinal?}`: `SOLICITADO` → `CONFIRMADO`. Emite E13 |
| POST | `/api/alquileres/{id}/rechazo` | `BRANCH_ADMIN` de la sucursal, `SYS_ADMIN` | `{motivo}`: `SOLICITADO` → `RECHAZADO` |
| POST | `/api/alquileres/{id}/cancelacion` | su cliente, el personal de su sucursal | `SOLICITADO` o `CONFIRMADO` → `CANCELADO`. Emite E16. Pagado → `409` (se cancela eliminando su viaje) |
| POST | `/api/alquileres/{id}/pago` | su cliente | `{fechaPago?}`: débito de la cartera, `PAGADO` y E14 en una transacción. `422` sin saldo; `409` sin confirmar o ya pagado |
| POST | `/api/alquileres/{id}/pago-mostrador` | `BRANCH_ADMIN`, `CASHIER` de la sucursal | `{fechaPago?}`: efectivo, `PAGADO` y E14 |
| GET | `/api/alquileres/reporte-ingresos` | `BRANCH_ADMIN` (su sucursal), `SYS_ADMIN` | Pagados y no cancelados por fecha de pago (`desde`, `hasta`, `sucursalId`): filas y total |

### Clientes — `/api/clientes`

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| GET | `/api/clientes` | `SYS_ADMIN`, `BRANCH_ADMIN`, `CASHIER` | Búsqueda paginada por `nit` o `nombre`, sin lápidas. `422` NIT inválido |

### Internas — `/internal` (red interna, sin gateway)

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| POST | `/internal/clientes/resolucion` | sales (S5) | `{nit?, nombre?}` → el cliente por NIT (lo crea si no existe; sin NIT, el genérico). `422` NIT inválido |
| GET | `/internal/clientes/por-usuario/{usuarioId}` | sales (S5) | El cliente de la cuenta; `409` si E4 todavía no llega |
| POST | `/internal/carteras/{clienteId}/debitos` | sales (S4) | `Idempotency-Key` (ventaId) y `{monto, fecha?, referenciaId?}` → `201 {resultado: APLICADO, saldo}`; repetido → `200`; `422` sin saldo; `409` anulado; `404` sin cartera |
| POST | `/internal/carteras/{clienteId}/debitos/{clave}/anulacion` | reconciliador de sales (S4) | Lápida `DEBITO_ANULADO` → `{resultado: ANULADO}`, o `APLICADO` si el débito ya había entrado |
| POST | `/internal/dlq/reprocesar` | operador | Reencola los mensajes de `customer.events.dlq` → `{reprocesados}` |

### Técnicas

| Método | Ruta | Qué es |
|---|---|---|
| GET | `/actuator/health` | Salud (healthcheck de Compose) |
| GET | `/v3/api-docs` · `/swagger-ui.html` | OpenAPI y Swagger UI |

## Levantar en desarrollo

Proyecto Compose propio (`docker-compose.yml` de esta carpeta): `customer-db` y el servicio. Los comandos van
desde `customer-service/`, salvo el de la infra. El [README del proyecto](../README.md#levantar-en-desarrollo)
lo describe con `stack.sh`, que todavía no existe; aquí va con `docker compose` directo.

1. **Infra**, desde la raíz del proyecto, con su `.env` copiado de `.env.example`. Va primero porque crea la red
   `transporte`, que este compose declara externa:

   ```bash
   docker compose up -d --wait                  # Redis, RabbitMQ y la red transporte
   ```

2. **`.env`** de esta carpeta. No hay nada que completar: `RABBITMQ_USER` y `RABBITMQ_PASSWORD` tienen que ser
   los de `../.env`, y los de la base pueden quedar como vienen:

   ```bash
   cp .env.example .env
   ```

3. **En Docker**:

   ```bash
   docker compose up -d --build --wait          # base y servicio; espera a que estén healthy
   curl localhost:8087/actuator/health          # {"status":"UP"}
   ```

   Swagger en http://localhost:8087/swagger-ui.html. Para el día a día:

   ```bash
   docker compose logs -f customer-service
   docker compose up -d --build customer-service   # después de cambiar el código
   docker compose down                          # baja todo; la base se conserva en su volumen
   docker compose down -v                       # además borra la base: Flyway la recrea al arrancar
   ```

4. **Fuera de Docker**, con Java 21 y Maven. Solo la base va en Docker. El servicio usa el mismo puerto que su
   contenedor, así que ese contenedor se detiene primero:

   ```bash
   docker compose stop customer-service         # si estaba arriba
   docker compose up -d --wait customer-db
   set -a; . ./.env; set +a; mvn spring-boot:run
   ```

   La base y RabbitMQ los encuentra en `localhost`, porque los dos se publican en `127.0.0.1`. El usuario de la
   base sale de `DB_USER` y `DB_PASSWORD`, no de `CUSTOMER_DB_USER`. Sus valores por defecto en
   `application.yml` son los del `.env.example`; si se cambiaron en `.env`, también hay que exportarlos.

5. **Pruebas**, con Docker corriendo. Testcontainers levanta su propio PostgreSQL y RabbitMQ, así que no usa la
   infra ni la base de arriba:

   ```bash
   mvn -q test                                  # todas, incluidas las *IT
   mvn -q test -Dtest=CarteraIT                 # una clase
   ```

6. **Probar**: buscar el cliente genérico ([`GET /api/clientes`](API.md#get-apiclientes)). Directo al 8087, los
   `X-User-*` que pone el gateway se ponen a mano:

   ```bash
   curl -s 'localhost:8087/api/clientes?nit=CF' \
     -H 'X-User-Id: 00000000-0000-0000-0000-000000000001' -H 'X-User-Roles: SYS_ADMIN'
   ```

   Devuelve una página con `Consumidor final` (`"generico": true`), que crea la migración inicial. Los clientes
   con cuenta aparecen cuando identity publica E4, al confirmar una cuenta. customer no llama a ningún otro
   servicio, así que no hace falta nada más arriba.
