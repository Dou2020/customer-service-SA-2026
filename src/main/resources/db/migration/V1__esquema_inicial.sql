-- customer_db: clientes por NIT, cartera con su libro de movimientos, alquileres y las réplicas
-- de tarifas y compras (DIAGRAMAS.md §7.6), más la outbox y el inbox de mensajería (copiadas de
-- identity).

-- Réplica de las tarifas de alquiler: se llena con ConfiguracionActualizada (E2). Como en
-- identity, nunca se actualiza: cada cambio es una versión nueva con su vigente_desde.
CREATE TABLE tarifa_alquiler (
    -- El mismo id de identity.tarifa (referencia por id, sin FK): un E2 repetido no duplica.
    id            bigint         PRIMARY KEY,
    tipo          varchar(20)    NOT NULL CHECK (tipo IN ('ALQUILER_KM', 'ALQUILER_DIA')),
    valor         numeric(12, 4) NOT NULL,
    vigente_desde date           NOT NULL,
    CONSTRAINT tarifa_alquiler_tipo_fecha_uq UNIQUE (tipo, vigente_desde)
);

CREATE TABLE cliente (
    id             uuid         PRIMARY KEY,
    -- Normalizado como en identity (Nit). 'CF' solo en la fila genérica; NULL es una cuenta
    -- con CF o la lápida de un cliente absorbido. La UQ arbitra el upsert de E4 contra el de S5.
    nit            varchar(15)  UNIQUE,
    nombre         varchar(150) NOT NULL,
    correo         varchar(150),
    telefono       varchar(15),
    -- Referencia por id a identity.usuario: sin FK, es otra base. Una cuenta, un cliente.
    usuario_id     uuid         UNIQUE,
    generico       boolean      NOT NULL DEFAULT false,
    -- Lápida: la cuenta que absorbió las compras de este cliente al tomar su NIT.
    absorbido_por  uuid         REFERENCES cliente (id),
    -- aggregateVersion del último PerfilGuardado (E4) aplicado
    perfil_version bigint,
    creado_en      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT cliente_generico_sin_usuario CHECK (NOT generico OR usuario_id IS NULL)
);

-- El cliente de las ventas sin NIT: una fila más, que nunca se enlaza a una cuenta ni tiene cartera.
INSERT INTO cliente (id, nit, nombre, generico) VALUES (gen_random_uuid(), 'CF', 'Consumidor final', true);

-- Nace al enlazar una cuenta: un cliente sin cuenta (ni el genérico) no tiene cartera.
CREATE TABLE cartera (
    cliente_id     uuid           PRIMARY KEY REFERENCES cliente (id),
    -- El débito es UPDATE … SET saldo = saldo - m WHERE saldo >= m (0 filas = saldo
    -- insuficiente); el CHECK es la red debajo de esa condición.
    saldo          numeric(12, 2) NOT NULL DEFAULT 0 CHECK (saldo >= 0),
    actualizado_en timestamptz    NOT NULL DEFAULT now()
);

-- Libro de la cartera: solo se insertan filas. La UQ de idempotency_key hace idempotente cada
-- movimiento, y con ella la lápida DEBITO_ANULADO (monto 0) del reconciliador impide que un
-- débito tardío con la misma clave se aplique.
CREATE TABLE movimiento_cartera (
    id               bigserial      PRIMARY KEY,
    cliente_id       uuid           NOT NULL REFERENCES cartera (cliente_id),
    tipo             varchar(20)    NOT NULL
        CHECK (tipo IN ('RECARGA', 'DEBITO_BOLETO', 'DEBITO_ALQUILER', 'DEBITO_ANULADO', 'REEMBOLSO')),
    monto            numeric(12, 2) NOT NULL,
    -- La que eligió el usuario (README, "Fechas"); registrado_en es la del sistema.
    fecha            date           NOT NULL,
    -- La venta o el alquiler que lo originó (sin FK: la venta es de sales).
    referencia_id    uuid,
    idempotency_key  varchar(64)    NOT NULL UNIQUE,
    saldo_resultante numeric(12, 2) NOT NULL,
    registrado_en    timestamptz    NOT NULL DEFAULT now(),
    CONSTRAINT movimiento_cartera_monto_positivo CHECK (monto > 0 OR tipo = 'DEBITO_ANULADO')
);

CREATE INDEX movimiento_cartera_cliente_idx ON movimiento_cartera (cliente_id);

CREATE TABLE alquiler (
    id              uuid           PRIMARY KEY,
    cliente_id      uuid           NOT NULL REFERENCES cliente (id),
    -- Referencia por id a identity.sucursal: la que atiende el alquiler (la elige el cliente).
    sucursal_id     uuid           NOT NULL,
    canal           varchar(10)    NOT NULL CHECK (canal IN ('ONLINE', 'PRESENCIAL')),
    origen          varchar(200)   NOT NULL,
    origen_lat      numeric(9, 6)  NOT NULL,
    origen_lng      numeric(9, 6)  NOT NULL,
    destino         varchar(200)   NOT NULL,
    destino_lat     numeric(9, 6)  NOT NULL,
    destino_lng     numeric(9, 6)  NOT NULL,
    distancia_km    numeric(8, 2)  NOT NULL,
    -- La que eligió el usuario al solicitarlo (README, "Fechas"); creado_en es la del sistema.
    fecha_solicitud date           NOT NULL,
    fecha_salida    date           NOT NULL,
    fecha_retorno   date,
    ida_y_vuelta    boolean        NOT NULL,
    pasajeros       smallint       NOT NULL,
    precio_estimado numeric(10, 2) NOT NULL,
    precio_final    numeric(10, 2),
    estado          varchar(15)    NOT NULL
        CHECK (estado IN ('SOLICITADO', 'CONFIRMADO', 'PAGADO', 'ASIGNADO', 'EN_CURSO', 'FINALIZADO',
                          'RECHAZADO', 'CANCELADO')),
    fecha_pago      date,
    -- Referencias por id a trip.viaje, fleet.bus y fleet.chofer: sin FK, son otras bases.
    viaje_id        uuid,
    bus_id          uuid,
    chofer_id       uuid,
    motivo          varchar(200),
    version         bigint         NOT NULL DEFAULT 0,
    creado_en       timestamptz    NOT NULL DEFAULT now()
);

CREATE INDEX alquiler_cliente_idx ON alquiler (cliente_id);
-- Alquileres pendientes de confirmar o asignar en la sucursal del admin.
CREATE INDEX alquiler_sucursal_estado_idx ON alquiler (sucursal_id, estado);

-- Réplica de las ventas para el historial: se llena con BoletosVendidos (E11) y se anula con
-- BoletosAnulados (E12), en línea y en mostrador.
CREATE TABLE compra_boleto (
    -- Referencia por id a sales.venta
    venta_id   uuid           PRIMARY KEY,
    cliente_id uuid           NOT NULL REFERENCES cliente (id),
    canal      varchar(10)    NOT NULL CHECK (canal IN ('ONLINE', 'PRESENCIAL')),
    -- Referencia por id a trip.viaje
    viaje_id   uuid           NOT NULL,
    origen     varchar(100)   NOT NULL,
    destino    varchar(100)   NOT NULL,
    salida     timestamptz    NOT NULL,
    asientos   smallint[]     NOT NULL,
    total      numeric(10, 2) NOT NULL,
    fecha_pago date           NOT NULL,
    estado     varchar(12)    NOT NULL CHECK (estado IN ('CONFIRMADA', 'ANULADA')),
    -- aggregateVersion de la venta (E11, E12)
    version    bigint         NOT NULL
);

CREATE INDEX compra_boleto_cliente_idx ON compra_boleto (cliente_id);

-- Outbox transaccional: el evento se inserta en la misma transacción que el
-- cambio y un relay lo publica (README, "Manejo de fallos").
CREATE TABLE outbox_event (
    id                bigserial   PRIMARY KEY,
    event_id          uuid        NOT NULL UNIQUE,
    tipo              varchar(80) NOT NULL,
    aggregate_id      varchar(64) NOT NULL,
    aggregate_version bigint      NOT NULL,
    payload           jsonb       NOT NULL,
    correlation_id    varchar(64),
    ocurrido_en       timestamptz NOT NULL DEFAULT now(),
    publicado_en      timestamptz
);

CREATE INDEX outbox_event_pendiente_idx ON outbox_event (id) WHERE publicado_en IS NULL;

-- Inbox: un evento ya aplicado por este consumidor se descarta.
CREATE TABLE processed_event (
    event_id     uuid        NOT NULL,
    consumidor   varchar(60) NOT NULL,
    procesado_en timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, consumidor)
);
