/**
 * Casos de uso que no son de un solo recurso o no tienen endpoint: los manejadores de eventos
 * (PerfilGuardado crea o enlaza el cliente por NIT y hace la absorción; E2, E11 y E12 alimentan
 * las réplicas de tarifas y compras; E8–E10 y E15 avanzan el alquiler), la resolución del cliente
 * por NIT que comparten S5 y el alquiler en mostrador, y la regla de alcance (un BRANCH_ADMIN
 * solo ve los alquileres de su sucursal, un CUSTOMER solo lo suyo) que aplican los *Service de
 * api/ con el recurso ya cargado. Lo propio de un recurso (transacción y @PreAuthorize) vive en
 * su *Service.
 */
package gt.cunoc.sa.customer.application;
