package br.com.carrefour.consolidado.adapter.in.job;

import br.com.carrefour.consolidado.domain.port.out.LancamentosGateway;
import br.com.carrefour.consolidado.domain.port.out.SaldoConsolidadoRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class ReconciliacaoDiariaJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliacaoDiariaJob.class);
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");

    private final LancamentosGateway lancamentosGateway;
    private final SaldoConsolidadoRepository repository;
    private final Counter divergenciasTotal;

    public ReconciliacaoDiariaJob(
            LancamentosGateway lancamentosGateway,
            SaldoConsolidadoRepository repository,
            MeterRegistry meterRegistry) {
        this.lancamentosGateway = lancamentosGateway;
        this.repository         = repository;
        this.divergenciasTotal  = Counter.builder("saldo_reconciliado_divergencias_total")
                .description("Divergências detectadas na reconciliação diária")
                .register(meterRegistry);
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void reconciliar() {
        var ontem = LocalDate.now().minusDays(1);

        log.atInfo()
                .addKeyValue("event", "reconciliacao_inicio")
                .addKeyValue("data",  ontem)
                .log("Reconciliação diária iniciada");

        try {
            var resumo   = lancamentosGateway.buscarResumoDiario(ontem);
            var saldoOpt = repository.buscarPorData(ontem);

            if (saldoOpt.isEmpty()) {
                if (resumo.totalLancamentos() > 0) {
                    log.atError()
                            .addKeyValue("event",       "reconciliacao_divergencia")
                            .addKeyValue("data",        ontem)
                            .addKeyValue("motivo",      "saldo_ausente")
                            .addKeyValue("lancamentos", resumo.totalLancamentos())
                            .log("Divergência: saldo consolidado ausente para data com lançamentos");
                    divergenciasTotal.increment();
                }
                return;
            }

            var saldo       = saldoOpt.get();
            var diffCreditos = resumo.totalCreditos().subtract(saldo.getTotalCreditos()).abs();
            var diffDebitos  = resumo.totalDebitos().subtract(saldo.getTotalDebitos()).abs();

            if (diffCreditos.compareTo(TOLERANCIA) > 0 || diffDebitos.compareTo(TOLERANCIA) > 0) {
                log.atError()
                        .addKeyValue("event",                "reconciliacao_divergencia")
                        .addKeyValue("data",                 ontem)
                        .addKeyValue("creditos_lancamentos", resumo.totalCreditos())
                        .addKeyValue("creditos_consolidado", saldo.getTotalCreditos())
                        .addKeyValue("debitos_lancamentos",  resumo.totalDebitos())
                        .addKeyValue("debitos_consolidado",  saldo.getTotalDebitos())
                        .log("Divergência de valores entre lançamentos e consolidado");
                divergenciasTotal.increment();
            } else {
                log.atInfo()
                        .addKeyValue("event",      "reconciliacao_ok")
                        .addKeyValue("data",       ontem)
                        .addKeyValue("lancamentos", resumo.totalLancamentos())
                        .log("Reconciliação concluída sem divergências");
            }
        } catch (Exception e) {
            log.atError()
                    .addKeyValue("event", "reconciliacao_erro")
                    .addKeyValue("data",  ontem)
                    .setCause(e)
                    .log("Erro na reconciliação diária");
        }
    }
}
