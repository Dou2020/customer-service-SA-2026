-- Lo que AlquilerAsignado (E15) deja en el alquiler. La placa y el nombre del chofer se copian
-- porque el historial del cliente los muestra y un CUSTOMER no puede consultar fleet;
-- asignacion_version es la versión del viaje del último E15 aplicado, para que una
-- reasignación vieja que llega tarde no pise a la nueva.
ALTER TABLE alquiler
    ADD COLUMN bus_placa          varchar(10),
    ADD COLUMN chofer_nombre      varchar(150),
    ADD COLUMN asignacion_version bigint;

-- El reporte de ingresos por alquiler filtra por sucursal y fecha de pago.
CREATE INDEX alquiler_sucursal_pago_idx ON alquiler (sucursal_id, fecha_pago) WHERE fecha_pago IS NOT NULL;
