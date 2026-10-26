# customer-service — API

Referencia de cada endpoint: qué entra, qué sale, qué errores da (con el `detail` exacto que muestra el
frontend) y qué efectos tiene. El resumen de una línea por endpoint está en el [README](README.md#endpoints);
las reglas de negocio, en el [README del proyecto](../README.md). La sección [Eventos](#eventos) es el
contrato con trip, sales, finance y notification.

| | URL base |
|---|---|
| Por el gateway (frontend) | `http://localhost:8080` — valida el JWT y aplica los límites |
| Directo al servicio (solo desarrollo) | `http://localhost:8087` — los `X-User-*` se ponen a mano |
| Entre servicios (red `transporte`) | `http://customer-service:8087` — solo `/internal/**` |
| Swagger | `http://localhost:8080/swagger-ui.html` (agregado) · `http://localhost:8087/swagger-ui.html` |

## Contenido

- [Convenciones](#convenciones)
  - [Autenticación](#autenticación) · [Formatos](#formatos) · [Paginación](#paginación) ·
    [Errores](#errores) · [Respuestas del gateway y límites](#respuestas-del-gateway-y-límites)
- [Modelos de respuesta](#modelos-de-respuesta)
- [Cartera — `/api/cartera`](#cartera--apicartera): [saldo](#get-apicartera) ·
  [movimientos](#get-apicarteramovimientos) · [recarga](#post-apicarterarecargas)
- [Historial — `/api/historial`](#historial--apihistorial)
- [Alquileres — `/api/alquileres`](#alquileres--apialquileres): [estimación](#post-apialquileresestimacion) ·
  [solicitud](#post-apialquileres) · [mostrador](#post-apialquileresmostrador) · [mios](#get-apialquileresmios) ·
  [lista](#get-apialquileres) · [detalle](#get-apialquileresid) · [confirmación](#post-apialquileresidconfirmacion) ·
  [rechazo](#post-apialquileresidrechazo) · [cancelación](#post-apialquileresidcancelacion) ·
  [pago](#post-apialquileresidpago) · [pago en mostrador](#post-apialquileresidpago-mostrador) ·
  [reporte de ingresos](#get-apialquileresreporte-ingresos)
- [Clientes — `/api/clientes`](#clientes--apiclientes)
- [Internas — `/internal`](#internas--internal): [cliente por NIT (S5)](#post-internalclientesresolucion) ·
  [cliente de una cuenta (S5)](#get-internalclientespor-usuariousuarioid) ·
  [débito (S4)](#post-internalcarterasclienteiddebitos) ·
  [anulación del débito (S4)](#post-internalcarterasclienteiddebitosclaveanulacion) ·
  [reprocesar DLQ](#post-internaldlqreprocesar)
- [Eventos](#eventos): [publica](#publica) · [consume](#consume)
- [Técnicas](#técnicas)

---

## Convenciones

Las mismas que identity-service; aquí va lo propio de customer y un enlace para lo común.

### Autenticación

Igual que en identity ([Autenticación](../identity-service/API.md#autenticación)): el gateway valida el
JWT y reenvía `X-User-Id`, `X-User-Roles`, `X-Sucursal-Id` y `X-Chofer-Id`. customer no tiene rutas
públicas: todo `/api/**` exige token. `X-User-Id` y `X-Sucursal-Id` son UUID (uno que no lo es se trata como
anónimo → `401`); `X-Chofer-Id` es el id del chofer en fleet, como texto (`3`), y no se valida. Llamando directo
al puerto 8087 en desarrollo, se ponen a mano:

```bash
curl -H 'X-User-Id: 0d3f5a1e-6c2b-4f7e-9a8d-1b2c3d4e5f60' -H 'X-User-Roles: CUSTOMER' \
     http://localhost:8087/api/historial
```

**Cuenta propia.** Cartera, historial y alquiler en línea son de la cuenta del token: el cliente sale de
`X-User-Id`, nunca de un id en la URL o el body. Están abiertos a **cualquier rol**, no solo a `CUSTOMER`:
identity emite `PerfilGuardado` para todas las cuentas y todos los usuarios pueden ser clientes. Mientras
customer no reciba el `PerfilGuardado` de una cuenta recién confirmada (milisegundos en la práctica),
estos endpoints responden `409 Tu cuenta se está terminando de configurar. Intenta de nuevo en unos
segundos`. El primer `SYS_ADMIN`, que identity crea al arrancar, no emite ese evento: tiene cliente desde
que guarda su perfil por primera vez.

**Alcance del personal.** Un `BRANCH_ADMIN` o `CASHIER` solo toca alquileres de su sucursal (la del
token); uno de otra → `403`. El `SYS_ADMIN` ve todas.

### Formatos

Como en identity ([Formatos](../identity-service/API.md#formatos)): JSON en UTF-8, ids UUID, instantes
ISO-8601 en UTC, fechas de negocio `AAAA-MM-DD` ("hoy" en `America/Guatemala`) y montos como número JSON.

Los ids de fleet, trip y sales (venta, viaje, bus, chofer) son **texto**, no UUID: esos servicios usan ids
numéricos, y customer los guarda y los devuelve tal cual llegan (`"101"`, o un UUID en texto), con hasta 64
caracteres. Siguen siendo UUID los de customer (cliente, alquiler) y los de identity (usuario, sucursal); el
de un movimiento de la cartera es un número. En JSON un UUID y un texto son el mismo string, así que el
frontend los lee igual.
Toda operación con fecha de negocio (recarga, pago, solicitud de alquiler) la recibe del usuario, con hoy
por defecto (README del proyecto, "Fechas").

Enumerados:

| Tipo | Valores |
|---|---|
| `EstadoAlquiler` | `SOLICITADO`, `CONFIRMADO`, `PAGADO`, `ASIGNADO`, `EN_CURSO`, `FINALIZADO`, `RECHAZADO`, `CANCELADO` |
| `Canal` | `ONLINE` (en línea, con cuenta), `PRESENCIAL` (mostrador) |
| `TipoMovimiento` | `RECARGA`, `DEBITO_BOLETO`, `DEBITO_ALQUILER`, `REEMBOLSO` (y `DEBITO_ANULADO`, que nunca se lista) |
| `EstadoCompra` | `CONFIRMADA`, `ANULADA` |

### Paginación

Paginan `GET /api/cartera/movimientos`, `GET /api/alquileres`, `GET /api/alquileres/mios` y
`GET /api/clientes`, con `page` (desde 0), `size` (20 por defecto) y `sort` (`campo,asc|desc`), y la
respuesta `{content, page}` de identity ([Paginación](../identity-service/API.md#paginación)). Un campo de
orden que no existe → `400 No se puede ordenar por '<campo>'`.

### Errores

Todo error sale como `ProblemDetail` (RFC 9457); los comunes (`400`, `401`, `403`, `404`, `405`, `500`)
son los de identity ([Errores](../identity-service/API.md#errores)). Lo propio de customer:

| Estado | `detail` | Cuándo |
|---|---|---|
| `409` | `Tu cuenta se está terminando de configurar. Intenta de nuevo en unos segundos` | Cuenta propia sin cliente todavía ([Autenticación](#autenticación)) |
| `409` | el de la transición (ver cada endpoint de alquiler) | Un cambio de estado que el ciclo de vida no permite |
| `422` | `El NIT solo puede tener dígitos y terminar en un dígito o K` | NIT con formato inválido (misma regla que identity; el dígito verificador no se valida) |
| `422` | `No tienes saldo suficiente en tu cartera. Recárgala e intenta de nuevo` | Débito de alquiler o de boletos sin saldo |
| `503` | `El servicio no está disponible en este momento. Intenta de nuevo en unos minutos` | Otro servicio no respondió (customer hoy no llama a nadie) |

Los `409` de concurrencia, cuando dos requests pasan a la vez la revisión del caso de uso y decide
PostgreSQL:

| `detail` | Cuándo |
|---|---|
| `Esa operación ya se registró` | Dos pagos simultáneos del mismo alquiler: el segundo choca con la clave del primero y no cobra |
| `Alguien más modificó estos datos mientras tanto. Vuelve a cargarlos e intenta de nuevo` | Dos cambios simultáneos del mismo alquiler (`@Version`) |
| `Otra operación está modificando estos datos. Intenta de nuevo` | No se obtuvo un bloqueo de PostgreSQL |
| `El cambio choca con datos que ya existen` | Otra restricción sin mensaje propio |

### Respuestas del gateway y límites

Las mismas respuestas del gateway que en identity
([Respuestas del gateway](../identity-service/API.md#respuestas-del-gateway-y-límites)), con customer en
lugar de identity en los `503` y `504`.

| Rutas | Clave | Límite |
|---|---|---|
| `POST /api/cartera/recargas` | usuario del token | 10 por minuto |
| Las demás: `/api/clientes/**`, `/api/cartera/**`, `/api/alquileres/**`, `/api/historial` | usuario del token | 20 por segundo, ráfagas de 40 |

`/internal/**` no tiene ruta en el gateway.

---

## Modelos de respuesta

### `CarteraResponse`

```json
{ "clienteId": "5d1f…", "saldo": 3000.00, "actualizadoEn": "2026-10-08T01:12:44.120Z" }
```

### `MovimientoResponse`

| Campo | Notas |
|---|---|
| `tipo` | `TipoMovimiento` |
| `fecha` | la que eligió el usuario (o la de pago de la venta o el alquiler) |
| `referenciaId` | la venta (el `referenciaId` que mandó sales en S4) o el alquiler que lo originó, como texto; `null` en una recarga |
| `saldoResultante` | saldo después del movimiento |

```json
{
  "id": 41, "tipo": "RECARGA", "monto": 3000.00, "fecha": "2026-10-07", "referenciaId": null,
  "saldoResultante": 3000.00, "registradoEn": "2026-10-08T01:12:44.118Z"
}
```

### `AlquilerResponse`

| Campo | Notas |
|---|---|
| `clienteNombre`, `clienteNit` | `clienteNit` es `null` en una cuenta sin NIT |
| `sucursalId` | la que atiende el alquiler; el nombre sale de `GET /api/sucursales` de identity |
| `distanciaKm` | línea recta entre origen y destino × el factor de carretera (1.3) |
| `idaYVuelta` | `true` si tiene `fechaRetorno` |
| `precioEstimado` | el que calculó el sistema al solicitarlo; no cambia aunque cambien las tarifas |
| `precioFinal` | el confirmado por la sucursal; `null` hasta entonces |
| `fechaPago` | la que eligió quien pagó; `null` hasta el pago |
| `viajeId`, `busId`, `busPlaca`, `choferId`, `choferNombre` | `null` hasta que trip asigna bus y chofer. `viajeId` (de trip) y `busId` y `choferId` (de fleet) son texto: `"101"`, `"7"`, `"3"` |
| `motivo` | del rechazo o la cancelación |

```json
{
  "id": "8f0c2c61-0d1b-4a39-9a57-3c1f4f7e2b10",
  "clienteId": "5d1f6a0e-…", "clienteNombre": "Ana López", "clienteNit": "12345679",
  "sucursalId": "1b4e28ba-2fa1-41d2-883f-0016d3cca427", "canal": "ONLINE",
  "origen": "Quetzaltenango", "origenLat": 14.834999, "origenLng": -91.518611,
  "destino": "Ciudad de Guatemala", "destinoLat": 14.634915, "destinoLng": -90.506882,
  "distanciaKm": 144.37, "fechaSolicitud": "2026-10-07", "fechaSalida": "2026-10-20",
  "fechaRetorno": "2026-10-21", "idaYVuelta": true, "pasajeros": 40,
  "precioEstimado": 2776.81, "precioFinal": null, "estado": "SOLICITADO", "fechaPago": null,
  "viajeId": null, "busId": null, "busPlaca": null, "choferId": null, "choferNombre": null,
  "motivo": null, "creadoEn": "2026-10-08T01:13:02.551Z"
}
```

### `EstimacionResponse`

```json
{
  "distanciaKm": 144.37, "idaYVuelta": true, "dias": 2,
  "tarifaKm": 6.5000, "cargoDia": 450.0000, "precioEstimado": 2776.81
}
```

`precioEstimado = distanciaKm × tarifaKm × (2 si es ida y vuelta) + dias × cargoDia`, redondeado al
centavo; `dias` cuenta el de salida y el de retorno (1 si es solo de ida).

### `ClienteResponse`

```json
{
  "id": "5d1f…", "nit": "12345679", "nombre": "Ana López", "correo": "ana@correo.gt",
  "telefono": "55551234", "generico": false, "conCuenta": true
}
```

---

## Cartera — `/api/cartera`

La cartera nace en cero cuando customer enlaza la cuenta (al aplicar `PerfilGuardado`). Su saldo solo
cambia con movimientos del libro, y cada uno tiene su `Idempotency-Key`.

### `GET /api/cartera`

**Acceso:** cuenta propia. **`200`** — `CarteraResponse`. `409` si la cuenta todavía no tiene cliente.

### `GET /api/cartera/movimientos`

**Acceso:** cuenta propia. Paginado, del más reciente al más antiguo (`sort=id,desc`).

| Query | Tipo | Descripción |
|---|---|---|
| `desde`, `hasta` | `AAAA-MM-DD` | opcionales, sobre `fecha` (la elegida); sin ellos, todos |

**`200`** — página de `MovimientoResponse`. Nunca incluye las lápidas `DEBITO_ANULADO` del reconciliador: no
movieron dinero.

### `POST /api/cartera/recargas`

Recarga manual con el monto y la fecha que indica el cliente.

**Acceso:** cuenta propia. Límite de 10 por minuto en el gateway.

| Encabezado | Reglas |
|---|---|
| `Idempotency-Key` | obligatorio; hasta 56 letras, números, `-` o `_`. El frontend genera uno (un UUID) por cada recarga y lo reusa si reintenta |

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `monto` | número | sí | de 0.01 a 10 000.00, con hasta 2 decimales |
| `fecha` | `AAAA-MM-DD` | no | hoy por defecto |

```json
{ "monto": 3000.00, "fecha": "2026-10-07" }
```

**`201`** — `MovimientoResponse` de la recarga. **`200`** — la misma respuesta si ya se registró con esa
clave: no acredita dos veces, tampoco si llegan dos a la vez.

| Estado | `detail` | Cuándo |
|---|---|---|
| `400` | `Falta el encabezado Idempotency-Key, o no es válido (hasta 56 letras, números, guiones o _)` | Sin clave o con caracteres de más |
| `409` | `Esa Idempotency-Key ya se usó en otra operación` | La clave ya se usó con otro monto o en otra cuenta |
| `409` | `Tu cuenta se está terminando de configurar. Intenta de nuevo en unos segundos` | Sin cliente todavía |

**Efectos:** movimiento `RECARGA` con la clave `RECARGA:<Idempotency-Key>`. La recarga no es ingreso
(supuesto 6): no emite eventos.

---

## Historial — `/api/historial`

### `GET /api/historial`

Boletos y alquileres de la cuenta, en línea y en mostrador, incluidos los anteriores a crear la cuenta si
se compraron con su NIT, y los que absorbió al cambiar de NIT.

**Acceso:** cuenta propia.

| Query | Tipo | Descripción |
|---|---|---|
| `desde`, `hasta` | `AAAA-MM-DD` | opcionales; los boletos se filtran por `fechaPago` y los alquileres por `fechaSolicitud` |

**`200`** — los más recientes primero:

```json
{
  "clienteId": "5d1f…", "nit": "12345679", "nombre": "Ana López",
  "boletos": [
    {
      "ventaId": "c0a8…", "canal": "PRESENCIAL", "viajeId": "101", "origen": "Quetzaltenango",
      "destino": "Ciudad de Guatemala", "salida": "2026-10-20T12:00:00Z", "asientos": [12, 13],
      "total": 70.00, "fechaPago": "2026-09-20", "estado": "CONFIRMADA"
    }
  ],
  "alquileres": [ { "id": "8f0c…", "estado": "PAGADO", "…": "AlquilerResponse" } ]
}
```

Los boletos son la réplica que customer arma con `BoletosVendidos` y `BoletosAnulados`: una compra recién
hecha aparece en cuanto llega el evento (milisegundos). Una compra anulada sigue apareciendo, como
`ANULADA`. `ventaId` y `viajeId` son los ids de sales y trip, como texto, tal cual los mandaron.

---

## Alquileres — `/api/alquileres`

Ciclo de vida en el README del proyecto ("Ciclo de vida de un alquiler"): el cliente (o el mostrador)
solicita, la sucursal confirma el precio o rechaza, el cliente paga, y trip asigna bus y chofer, registra la
salida y la llegada o elimina el viaje (eso llega por eventos, ver [Eventos](#consume)).

Campos comunes de las solicitudes:

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `trayecto.origen`, `trayecto.destino` | string | sí | hasta 200; el nombre que se muestra |
| `trayecto.origenLat`, `trayecto.destinoLat` | número | sí | de −90 a 90, hasta 6 decimales |
| `trayecto.origenLng`, `trayecto.destinoLng` | número | sí | de −180 a 180, hasta 6 decimales |
| `fechaSolicitud` | `AAAA-MM-DD` | no | hoy por defecto; las tarifas que se aplican son las vigentes a esta fecha |
| `fechaSalida` | `AAAA-MM-DD` | sí | no antes de `fechaSolicitud` |
| `fechaRetorno` | `AAAA-MM-DD` | no | si viene, es ida y vuelta; no antes de `fechaSalida` |
| `pasajeros` | entero | sí (no en la estimación) | de 1 a 100 |

Errores comunes de la estimación y de las dos solicitudes:

| Estado | `detail` | Cuándo |
|---|---|---|
| `422` | `La fecha de salida no puede ser anterior a la de la solicitud` | |
| `422` | `La fecha de retorno no puede ser anterior a la de salida` | |
| `422` | `El origen y el destino están en el mismo lugar` | La distancia estimada no llega a 1 km |
| `422` | `Todavía no hay tarifas de alquiler vigentes a esa fecha: el administrador del sistema tiene que configurarlas` | Falta `ALQUILER_KM` o `ALQUILER_DIA` vigente a `fechaSolicitud` (`POST /api/tarifas` de identity) |

### `POST /api/alquileres/estimacion`

Calcula el precio sin guardar nada: es la vista previa del formulario.

**Acceso:** cualquier usuario autenticado. Body: `trayecto`, `fechaSolicitud`, `fechaSalida`, `fechaRetorno`.

```json
{
  "trayecto": {
    "origen": "Quetzaltenango", "origenLat": 14.834999, "origenLng": -91.518611,
    "destino": "Ciudad de Guatemala", "destinoLat": 14.634915, "destinoLng": -90.506882
  },
  "fechaSalida": "2026-10-20",
  "fechaRetorno": "2026-10-21"
}
```

**`200`** — `EstimacionResponse`.

### `POST /api/alquileres`

Solicitud en línea.

**Acceso:** cuenta propia (el cliente es el del token). Body: los campos comunes más:

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `sucursalId` | UUID | sí | la que atiende el alquiler, elegida de `GET /api/sucursales` (identity). customer no la valida: con un id que no existe el alquiler queda sin nadie que lo confirme |

**`201`** — `AlquilerResponse` en `SOLICITADO`, con `precioEstimado`; `Location: /api/alquileres/{id}`.
`409` si la cuenta todavía no tiene cliente.

### `POST /api/alquileres/mostrador`

El cajero o el admin registra la solicitud de un cliente que llega a la sucursal.

**Acceso:** `BRANCH_ADMIN` y `CASHIER`. La sucursal es la del token. Body: los campos comunes más:

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `nit` | string | no | hasta 20; misma regla que identity. Vacío o `CF` es el cliente genérico (consumidor final). Si el NIT no es cliente, se crea; si es de una cuenta, el alquiler aparece en su historial |
| `nombre` | string | no | hasta 150; solo para un NIT nuevo (si no, `Cliente NIT <nit>`). No renombra a un cliente que ya existe |

**`201`** — `AlquilerResponse` con `canal: PRESENCIAL`. `422` con el mensaje de formato del NIT.

### `GET /api/alquileres/mios`

**Acceso:** cuenta propia. Paginado, del más reciente al más antiguo. **`200`** — página de `AlquilerResponse`.

### `GET /api/alquileres`

Para el personal: los pendientes de confirmar (`estado=SOLICITADO`), de asignar (`estado=PAGADO`), etc.

**Acceso:** `SYS_ADMIN`; `BRANCH_ADMIN` y `CASHIER`, solo su sucursal.

| Query | Tipo | Descripción |
|---|---|---|
| `estado` | `EstadoAlquiler` | opcional |
| `sucursalId` | UUID | opcional; solo lo usa el `SYS_ADMIN`. A un `BRANCH_ADMIN` o `CASHIER` no le amplía el alcance |

**`200`** — página de `AlquilerResponse`, por `fechaSalida` ascendente.

### `GET /api/alquileres/{id}`

**Acceso:** su cliente, el personal de su sucursal o un `SYS_ADMIN`; otro → `403`. **`200`** —
`AlquilerResponse`. `404 No existe ese alquiler`.

### `POST /api/alquileres/{id}/confirmacion`

Confirma el precio: el estimado o el que fije la sucursal.

**Acceso:** `BRANCH_ADMIN` de la sucursal del alquiler, o `SYS_ADMIN`.

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `precioFinal` | número | no | mayor que 0, hasta 8 enteros y 2 decimales. Sin él (o sin body), se acepta el estimado |

**`200`** — `AlquilerResponse` en `CONFIRMADO`.

| Estado | `detail` | Cuándo |
|---|---|---|
| `409` | `Solo se confirma un alquiler solicitado, y este está <estado>` | |

**Efectos:** emite E13 `customer.alquiler.confirmado`: notification le avisa al cliente que ya puede pagar.

### `POST /api/alquileres/{id}/rechazo`

**Acceso:** igual que la confirmación.

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `motivo` | string | sí | hasta 200; el cliente lo ve en su historial |

**`200`** — `AlquilerResponse` en `RECHAZADO`. `409 Solo se rechaza un alquiler solicitado, y este está <estado>`.
No emite eventos: el catálogo no tiene uno para el rechazo.

### `POST /api/alquileres/{id}/cancelacion`

Antes de pagar (supuesto 12).

**Acceso:** su cliente, o el personal de su sucursal (o `SYS_ADMIN`). Body opcional: `motivo` (hasta 200).

**`200`** — `AlquilerResponse` en `CANCELADO`.

| Estado | `detail` | Cuándo |
|---|---|---|
| `409` | `Un alquiler pagado ya no se cancela aquí: la sucursal lo cancela eliminando su viaje` | `PAGADO` o `ASIGNADO` |
| `409` | `El alquiler ya está <estado>` | `EN_CURSO`, `FINALIZADO`, `RECHAZADO` o `CANCELADO` |

**Efectos:** emite E16 `customer.alquiler.cancelado` con `estabaPagado: false` y `reembolso: NINGUNO`.

### `POST /api/alquileres/{id}/pago`

Pago con la cartera: el débito, el cambio a `PAGADO` y el evento van en una sola transacción local.

**Acceso:** solo su cliente (otro, aunque sea el personal → `403`).

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `fechaPago` | `AAAA-MM-DD` | no | hoy por defecto; es la que usan los reportes de ingresos |

**`200`** — `AlquilerResponse` en `PAGADO`.

| Estado | `detail` | Cuándo |
|---|---|---|
| `409` | `La sucursal todavía no confirma el precio de este alquiler` | `SOLICITADO` |
| `409` | `Este alquiler ya está pagado` | Pagado, asignado, en curso o finalizado |
| `409` | `El alquiler está <estado>` | `RECHAZADO` o `CANCELADO` |
| `409` | `Esa operación ya se registró` | Dos pagos simultáneos: el segundo no cobra |
| `422` | `No tienes saldo suficiente en tu cartera. Recárgala e intenta de nuevo` | No cambia nada |

**Efectos:** movimiento `DEBITO_ALQUILER` por el `precioFinal` (clave `ALQUILER:<id>`) y E14
`customer.alquiler.pagado` con `metodoPago: CARTERA`: trip crea el viaje, finance registra el ingreso y
notification manda el comprobante.

### `POST /api/alquileres/{id}/pago-mostrador`

Cobro en efectivo en la sucursal, de un alquiler presencial o de uno en línea.

**Acceso:** `BRANCH_ADMIN` y `CASHIER` de la sucursal del alquiler. Body opcional: `fechaPago`.

**`200`** — `AlquilerResponse` en `PAGADO`. Los mismos `409` de estado que el pago en línea.

**Efectos:** E14 con `metodoPago: EFECTIVO`; no toca la cartera.

### `GET /api/alquileres/reporte-ingresos`

Reporte de ingresos por alquiler de buses (README del proyecto, "Reportes"): los alquileres pagados que no
se cancelaron, por fecha de pago.

**Acceso:** `BRANCH_ADMIN` (siempre su sucursal) y `SYS_ADMIN` (todas, o una con `sucursalId`).

| Query | Tipo | Descripción |
|---|---|---|
| `desde`, `hasta` | `AAAA-MM-DD` | opcionales, sobre `fechaPago`; sin ellos, todos los registros |
| `sucursalId` | UUID | opcional, solo `SYS_ADMIN` |

**`200`**:

```json
{
  "desde": "2026-10-01", "hasta": "2026-10-31", "sucursalId": "1b4e…",
  "filas": [
    {
      "alquilerId": "8f0c…", "sucursalId": "1b4e…", "cliente": "Ana López", "nit": "12345679",
      "canal": "ONLINE", "origen": "Quetzaltenango", "destino": "Ciudad de Guatemala",
      "fechaSalida": "2026-10-20", "fechaRetorno": "2026-10-21", "busPlaca": "P-123ABC",
      "choferNombre": "Pedro Pérez", "fechaPago": "2026-10-08", "precio": 1800.00
    }
  ],
  "cantidad": 1,
  "total": 1800.00
}
```

`busPlaca` y `choferNombre` son `null` hasta que trip asigna.

---

## Clientes — `/api/clientes`

### `GET /api/clientes`

Busca clientes para el mostrador: por NIT, para mostrar el nombre antes de vender o alquilar.

**Acceso:** `SYS_ADMIN`, `BRANCH_ADMIN` y `CASHIER`. Los clientes no son de una sucursal: cualquiera
compra en cualquiera.

| Query | Tipo | Descripción |
|---|---|---|
| `nit` | string | opcional; se normaliza como en identity (`CF` encuentra al genérico). `422` si no es válido |
| `nombre` | string | opcional; contiene, sin distinguir mayúsculas |

**`200`** — página de `ClienteResponse`, por `nombre`. No incluye los clientes absorbidos por una cuenta.

---

## Internas — `/internal`

Para sales y para el operador. El gateway **no** las expone y no piden autenticación: en producción solo
se llega a ellas desde la red interna de Docker.

### `POST /internal/clientes/resolucion`

S5 en mostrador: el cliente de una venta por su NIT.

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `nit` | string | no | hasta 20. Vacío o `CF` → el cliente genérico |
| `nombre` | string | no | hasta 150; solo para un NIT nuevo |

**`200`**:

```json
{
  "clienteId": "5d1f…", "nit": "12345679", "nombre": "Ana López", "correo": "ana@correo.gt",
  "generico": false, "conCuenta": true
}
```

`correo` es `null` en un cliente de mostrador sin cuenta (sales lo manda en `BoletosVendidos` para el
correo de confirmación). `422` con el mensaje de formato del NIT.

**Efectos:** si el NIT no existe, crea el cliente. Dos cajeros a la vez, o un cajero y la confirmación
de una cuenta con ese NIT, terminan con un solo cliente: la UQ de `cliente.nit` decide (README del
proyecto, "Carreras y su árbitro").

### `GET /internal/clientes/por-usuario/{usuarioId}`

S5 en línea: el cliente de la cuenta que compra. **`200`** — el mismo cuerpo. `409 Tu cuenta se está
terminando de configurar. Intenta de nuevo en unos segundos` si customer todavía no aplicó su
`PerfilGuardado`.

### `POST /internal/carteras/{clienteId}/debitos`

S4: el débito que paga una compra de boletos en línea.

| Encabezado | Reglas |
|---|---|
| `Idempotency-Key` | obligatorio, hasta 64; el `ventaId`. sales puede reintentar con la misma clave |

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `monto` | número | sí | mayor que 0, hasta 2 decimales |
| `fecha` | `AAAA-MM-DD` | no | la fecha de pago que eligió el comprador; hoy por defecto |
| `referenciaId` | UUID | no | la venta. S4 sigue pidiendo un UUID; el movimiento lo guarda como texto, igual que los demás ids de sales |

**`201`** al aplicarlo; **`200`** con el mismo cuerpo si ya se había aplicado con esa clave (un solo cargo):

```json
{ "resultado": "APLICADO", "movimientoId": 57, "monto": 70.00, "saldo": 2930.00 }
```

| Estado | `detail` | Cuándo |
|---|---|---|
| `400` | `Falta el encabezado Idempotency-Key, o tiene más de 64 caracteres` | |
| `404` | `Ese cliente no tiene cartera` | El genérico, o un cliente de mostrador sin cuenta |
| `409` | `Este pago se anuló porque no se pudo confirmar a tiempo. Intenta la compra de nuevo` | El reconciliador ya puso la lápida de esa clave |
| `409` | `Esa Idempotency-Key ya se usó en otra operación` | Misma clave con otro monto, otro cliente u otro tipo |
| `422` | `No tienes saldo suficiente en tu cartera. Recárgala e intenta de nuevo` | No cambia nada |

**Efectos:** `UPDATE cartera SET saldo = saldo - monto WHERE … AND saldo >= monto` y el movimiento
`DEBITO_BOLETO`, en una transacción: dos débitos simultáneos nunca dejan el saldo en negativo.

### `POST /internal/carteras/{clienteId}/debitos/{clave}/anulacion`

S4: el reconciliador de sales resuelve un débito incierto (timeout: no se sabe si se aplicó). No consulta:
inserta la lápida `DEBITO_ANULADO` (monto 0) con la misma clave, y la UQ decide.

**`200`**:

```json
{ "resultado": "ANULADO" }
```

| `resultado` | Significa | sales… |
|---|---|---|
| `ANULADO` | El débito no había entrado, y ya no podrá: un débito tardío con esa clave recibe `409` | libera los asientos |
| `APLICADO` | El débito sí había entrado | confirma la venta |

Es idempotente (repetirla da lo mismo). `404 Ese cliente no tiene cartera`; `409 Esa Idempotency-Key ya se
usó en otra operación` si la clave es de una recarga o un pago de alquiler.

### `POST /internal/dlq/reprocesar`

Devuelve a la cola `customer.events` los mensajes de `customer.events.dlq`, una vez corregida la causa por
la que fallaron.

**Llama:** el operador, desde el servidor. Sin body.

```bash
docker compose -f customer-service/docker-compose.yml exec customer-service \
  curl -s -X POST localhost:8087/internal/dlq/reprocesar
```

**`200`**:

```json
{ "reprocesados": 3 }
```

**Efectos:**

- Mueve solo los mensajes que había al empezar. Uno que vuelve a fallar regresa a la DLQ después de sus
  reintentos y no se reprocesa en un ciclo sin fin.
- Cada mensaje sale de la DLQ solo después de que RabbitMQ confirma el reenvío, así que un fallo a la
  mitad no pierde ninguno.
- Si RabbitMQ no responde, da `500`.

---

## Eventos

Sobre común en el README del proyecto ("Sobre de cada evento"). Exchange `transporte.events`, cola
`customer.events` con su DLQ. Los consumidores ignoran los campos que no usan, así que agregar uno no
rompe a nadie; quitar o renombrar uno, sí.

### Publica

Por outbox, en la transacción del cambio. `aggregateId` = `alquilerId` y `aggregateVersion` = la
versión del alquiler después del cambio.

**E13 `customer.alquiler.confirmado`** → notification. `correo` es `null` si el cliente no tiene.

```json
{
  "alquilerId": "8f0c…", "sucursalId": "1b4e…", "correo": "ana@correo.gt", "nombre": "Ana López",
  "origen": "Quetzaltenango", "destino": "Ciudad de Guatemala",
  "fechaSalida": "2026-10-20", "fechaRetorno": "2026-10-21", "precioFinal": 1800.00
}
```

**E14 `customer.alquiler.pagado`** → trip (crea el viaje `ALQUILER` pendiente de asignación), finance
(ingreso de la sucursal en `fechaPago`) y notification (comprobante). `nit` es `"CF"` si no tiene;
`metodoPago` es `CARTERA` o `EFECTIVO`.

```json
{
  "alquilerId": "8f0c…", "clienteId": "5d1f…", "sucursalId": "1b4e…", "nombre": "Ana López",
  "nit": "12345679", "correo": "ana@correo.gt",
  "origen": "Quetzaltenango", "origenLat": 14.834999, "origenLng": -91.518611,
  "destino": "Ciudad de Guatemala", "destinoLat": 14.634915, "destinoLng": -90.506882,
  "distanciaKm": 144.37, "fechaSalida": "2026-10-20", "fechaRetorno": "2026-10-21", "idaYVuelta": true,
  "pasajeros": 40, "monto": 1800.00, "fechaPago": "2026-10-08", "metodoPago": "CARTERA"
}
```

**E16 `customer.alquiler.cancelado`** → finance (reversa el ingreso solo si `estabaPagado`) y
notification. `monto` es el precio final (o el estimado si no se confirmó); `fechaPago` es la del cobro
original (`null` si no estaba pagado); `fecha` es la de la cancelación; `reembolso` es `CARTERA` (devuelto
a la cartera), `CAJA` (se pagó en efectivo: se devuelve en la sucursal) o `NINGUNO` (no se había cobrado).

```json
{
  "alquilerId": "8f0c…", "sucursalId": "1b4e…", "correo": "ana@correo.gt", "nombre": "Ana López",
  "estabaPagado": true, "monto": 1800.00, "fechaPago": "2026-10-08", "fecha": "2026-10-15",
  "reembolso": "CARTERA", "motivo": "Se eliminó el viaje del alquiler"
}
```

### Consume

Cada evento se aplica en una transacción con su inbox (`processed_event`): uno repetido no tiene efecto.
Lo transitorio se reintenta a 1, 2, 4 y 8 s y después va a la DLQ; lo que nunca va a funcionar (payload
ilegible, dato obligatorio que falta, un id vacío o de más de 64 caracteres, alquiler o cliente que no
existe) va directo a la DLQ.

**Ids de fleet, trip y sales: texto.** `viajeId`, `busId`, `choferId` y `ventaId` se leen como string y se
guardan tal cual: el id numérico del productor (`"101"`) o un UUID en texto. Un número JSON (`101`) también
entra, como el mismo texto. Vacío o de más de 64 caracteres → DLQ. `alquilerId` y `clienteId` siguen siendo
UUID: son ids de customer.

Los productores que todavía no publican en `transporte.events` (trip, sales) tienen que mandar **al menos**
estos campos:

| Evento | Payload que customer lee | Qué hace |
|---|---|---|
| E2 `identity.configuracion.actualizada` | `{tarifaId, tipo, valor, vigenteDesde}` | Guarda la versión de `ALQUILER_KM` o `ALQUILER_DIA` con el id de identity; descarta `DEPRECIACION_KM` |
| E4 `identity.perfil.guardado` | `{usuarioId, nit, nombreCompleto, correo, telefono}`; `aggregateVersion` = versión del perfil | Crea el cliente de la cuenta o lo enlaza por NIT; al cambiar el NIT, absorbe lo que el NIT nuevo compró en mostrador (README del proyecto, "PerfilGuardado en customer"). Una versión vieja se descarta. El NIT de otra cuenta es reintentable y luego DLQ |
| E8 `trip.viaje.iniciado` · E9 `trip.viaje.finalizado` · E10 `trip.viaje.eliminado` | `{viajeId, tipo, alquilerId}`; `alquilerId` es el del E14 que creó el viaje, y `null` en uno `REGULAR`. `viajeId` (texto) es opcional: E8 y E9 lo dejan en el alquiler si E15 no lo dejó antes (vacío o de más de 64 → DLQ); E10 no lo lee | Los `REGULAR` se ignoran, sin revisar nada más. E8 → `EN_CURSO`; E9 → `FINALIZADO`; E10 sobre uno `PAGADO` o `ASIGNADO` → `CANCELADO`, reembolso a la cartera si se pagó con ella (`REEMBOLSO`, clave `REEMBOLSO-ALQUILER:<id>`, `fecha` = `occurredAt` en Guatemala) y E16 |
| E15 `trip.alquiler.asignado` | `{alquilerId, viajeId, busId, placa, choferId, choferNombre}`; `viajeId`, `busId` y `choferId` obligatorios, texto; `aggregateVersion` = versión del viaje | Guarda bus y chofer (`PAGADO` → `ASIGNADO`). trip lo emite **en cada asignación o reasignación**; una reasignación más vieja que la última aplicada se descarta |
| E11 `sales.boletos.vendidos` | `{ventaId, clienteId, canal, viajeId, origen, destino, salida, asientos, total, fechaPago}`; `ventaId` y `viajeId` obligatorios, texto; `aggregateVersion` = versión de la venta | Agrega la compra al historial (`CONFIRMADA`). Si el cliente fue absorbido, sigue la lápida hasta la cuenta. Las del genérico `CF` no se guardan: son anónimas |
| E12 `sales.boletos.anulados` | los mismos campos de E11 y `motivo` | Marca la compra `ANULADA` (aunque llegue antes que E11: gana la versión mayor) y devuelve a la cartera **exactamente lo que se debitó** con la clave `ventaId` (`REEMBOLSO`, clave `REEMBOLSO:<ventaId>`, hasta 74 caracteres). Sin débito (mostrador, o pago anulado por la lápida) no devuelve nada |

---

## Técnicas

| Método | Ruta | Qué devuelve |
|---|---|---|
| `GET` | `/actuator/health` | `200 {"status":"UP"}`, o `503 {"status":"DOWN"}` si cae la base o RabbitMQ. Público; lo usa el healthcheck de Compose. El gateway no lo expone |
| `GET` | `/v3/api-docs` | Definición OpenAPI 3 en JSON. Por el gateway: `/v3/api-docs/customer` |
| `GET` | `/swagger-ui.html` | Swagger UI del servicio. Las rutas protegidas se prueban con el botón *Authorize* (`bearerAuth`) desde el Swagger del gateway |
