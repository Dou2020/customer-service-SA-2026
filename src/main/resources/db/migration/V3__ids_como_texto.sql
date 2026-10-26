-- Los ids de fleet, trip y sales pasan a texto (decisión del 2026-10-25): esos servicios usan
-- bigserial y mandan su id tal cual ("123"), y un UUID en texto sigue entrando igual. customer
-- nunca los interpreta: solo los guarda, los compara y los devuelve. Los ids propios (cliente,
-- alquiler) y los de identity (sucursal, usuario) siguen siendo uuid. Ninguna de estas columnas
-- tiene FK ni índice propio fuera de la PK de compra_boleto, que PostgreSQL reconstruye solo.

-- Réplica de E11/E12: la venta de sales y su viaje de trip.
ALTER TABLE compra_boleto
    ALTER COLUMN venta_id TYPE varchar(64) USING venta_id::text,
    ALTER COLUMN viaje_id TYPE varchar(64) USING viaje_id::text;

-- Lo que dejan E15, E8 y E9: el viaje de trip y el bus y el chofer de fleet. La placa que copia
-- E15 pasa de 10 a 20, el largo de bus.placa en fleet: con 10, un E15 con una placa más larga
-- fallaba en la base, se reintentaba y terminaba en la DLQ. finance acepta el mismo largo
-- (costo_viaje.placa_bus, su V3).
ALTER TABLE alquiler
    ALTER COLUMN viaje_id  TYPE varchar(64) USING viaje_id::text,
    ALTER COLUMN bus_id    TYPE varchar(64) USING bus_id::text,
    ALTER COLUMN chofer_id TYPE varchar(64) USING chofer_id::text,
    ALTER COLUMN bus_placa TYPE varchar(20);

-- referencia_id es la venta (S4) o el alquiler que originó el movimiento: con la venta de sales
-- como texto, la columna también, aunque S4 siga pidiendo un UUID en referenciaId (sales genera
-- uno por compra) y el alquiler sea nuestro.
--
-- idempotency_key crece de 64 a 80 por la clave del reembolso de una venta, REEMBOLSO:<ventaId>:
-- con el ventaId ya aceptado como texto de hasta 64 (el límite del Idempotency-Key de S4), esa
-- clave llega a 74 y no cabía; con un UUID eran 46. Agrandar un varchar no reescribe la tabla ni
-- su índice único.
ALTER TABLE movimiento_cartera
    ALTER COLUMN referencia_id   TYPE varchar(64) USING referencia_id::text,
    ALTER COLUMN idempotency_key TYPE varchar(80);
