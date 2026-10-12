package gt.cunoc.sa.customer.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import gt.cunoc.sa.customer.api.alquiler.TarifaAlquilerRepository;
import gt.cunoc.sa.customer.domain.TipoTarifa;
import gt.cunoc.sa.customer.infrastructure.messaging.Inbox;
import gt.cunoc.sa.customer.infrastructure.messaging.Payloads;
import gt.cunoc.sa.customer.infrastructure.messaging.Sobre;
import gt.cunoc.sa.customer.infrastructure.messaging.eventos.ConfiguracionActualizada;

/**
 * Aplica ConfiguracionActualizada (E2): guarda cada versión de las tarifas de alquiler, no solo la
 * última, porque el precio estimado usa la vigente a la fecha de la solicitud, que puede ser
 * pasada. Las de depreciación son de finance.
 */
@Service
public class ReplicaTarifas {

    private static final Logger log = LoggerFactory.getLogger(ReplicaTarifas.class);

    private final Inbox inbox;
    private final TarifaAlquilerRepository tarifas;
    private final ObjectMapper json;

    public ReplicaTarifas(Inbox inbox, TarifaAlquilerRepository tarifas, ObjectMapper json) {
        this.inbox = inbox;
        this.tarifas = tarifas;
        this.json = json;
    }

    @Transactional
    public void aplicar(Sobre sobre) {
        if (!inbox.primeraVez(sobre.eventId())) {
            return;
        }
        ConfiguracionActualizada tarifa = Payloads.leer(json, sobre, ConfiguracionActualizada.class);
        Payloads.exigir(tarifa.tipo() != null && tarifa.valor() != null && tarifa.vigenteDesde() != null, sobre,
                "tipo, valor o vigenteDesde");
        if (!esDeAlquiler(tarifa.tipo())) {
            log.debug("Tarifa {} no es de alquiler; se descarta", tarifa.tipo());
            return;
        }
        // Por id de identity: la misma versión que llega otra vez (otro eventId) no se duplica.
        tarifas.insertar(tarifa.tarifaId(), tarifa.tipo(), tarifa.valor(), tarifa.vigenteDesde());
    }

    private static boolean esDeAlquiler(String tipo) {
        return tipo.equals(TipoTarifa.ALQUILER_KM.name()) || tipo.equals(TipoTarifa.ALQUILER_DIA.name());
    }
}
