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
        this.repository = repository;
        this.divergenciasTotal = Counter.builder("saldo_reconciliado_divergencias_total")
                .description("Divergências detectadas na reconciliação diária")
                .register(meterRegistry);
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void reconciliar() {
        var ontem = LocalDate.now().minusDays(1);
        log.info("reconciliacao_inicio data={}", ontem);

        try {
            var resumo = lancamentosGateway.buscarResumoDiario(ontem);
            var saldoOpt = repository.buscarPorData(ontem);

            if (saldoOpt.isEmpty()) {
                if (resumo.totalLancamentos() > 0) {
                    log.error("reconciliacao_divergencia data={} motivo=saldo_ausente lancamentos={}",
                            ontem, resumo.totalLancamentos());
                    divergenciasTotal.increment();
                }
                return;
            }

            var saldo = saldoOpt.get();
            var diffCreditos = resumo.totalCreditos().subtract(saldo.getTotalCreditos()).abs();
            var diffDebitos  = resumo.totalDebitos().subtract(saldo.getTotalDebitos()).abs();

            if (diffCreditos.compareTo(TOLERANCIA) > 0 || diffDebitos.compareTo(TOLERANCIA) > 0) {
                log.error("reconciliacao_divergencia data={} creditos_lancamentos={} creditos_consolidado={} debitos_lancamentos={} debitos_consolidado={}",
                        ontem,
                        resumo.totalCreditos(), saldo.getTotalCreditos(),
                        resumo.totalDebitos(), saldo.getTotalDebitos());
                divergenciasTotal.increment();
            } else {
                log.info("reconciliacao_ok data={} lancamentos={}", ontem, resumo.totalLancamentos());
            }
        } catch (Exception e) {
            log.error("reconciliacao_erro data={} motivo={}", ontem, e.getMessage());
        }
    }
}
