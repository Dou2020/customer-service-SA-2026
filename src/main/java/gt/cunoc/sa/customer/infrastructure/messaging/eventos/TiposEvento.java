package gt.cunoc.sa.customer.infrastructure.messaging.eventos;

/** Routing keys (dominio.agregado.evento) de lo que customer publica y consume. */
public final class TiposEvento {

    public static final String ALQUILER_CONFIRMADO = "customer.alquiler.confirmado";           // E13
    public static final String ALQUILER_PAGADO = "customer.alquiler.pagado";                   // E14
    public static final String ALQUILER_CANCELADO = "customer.alquiler.cancelado";             // E16

    public static final String CONFIGURACION_ACTUALIZADA = "identity.configuracion.actualizada"; // E2 (consume)
    public static final String PERFIL_GUARDADO = "identity.perfil.guardado";                   // E4 (consume)
    public static final String VIAJE_INICIADO = "trip.viaje.iniciado";                         // E8 (consume)
    public static final String VIAJE_FINALIZADO = "trip.viaje.finalizado";                     // E9 (consume)
    public static final String VIAJE_ELIMINADO = "trip.viaje.eliminado";                       // E10 (consume)
    public static final String BOLETOS_VENDIDOS = "sales.boletos.vendidos";                    // E11 (consume)
    public static final String BOLETOS_ANULADOS = "sales.boletos.anulados";                    // E12 (consume)
    public static final String ALQUILER_ASIGNADO = "trip.alquiler.asignado";                   // E15 (consume)

    private TiposEvento() {
    }
}
