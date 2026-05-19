package br.com.carrefour.consolidado.application.usecase;

import br.com.carrefour.consolidado.domain.model.AuditEvento;
import br.com.carrefour.consolidado.domain.model.SaldoConsolidado;
import br.com.carrefour.consolidado.domain.port.in.ReconstruirConsolidadoUseCase;
import br.com.carrefour.consolidado.domain.port.out.AuditPublisher;
import br.com.carrefour.consolidado.domain.port.out.LancamentosGateway;
import br.com.carrefour.consolidado.domain.port.out.SaldoConsolidadoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Service
public class ReconstruirConsolidadoService implements ReconstruirConsolidadoUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReconstruirConsolidadoService.class);
    private static final int PERIODO_MAXIMO_DIAS = 90;

    private final LancamentosGateway       lancamentosGateway;
    private final SaldoConsolidadoRepository repository;
    private final ReconstruirPorDataHelper helper;
    private final AuditPublisher           auditPublisher;

    public ReconstruirConsolidadoService(
            LancamentosGateway lancamentosGateway,
            SaldoConsolidadoRepository repository,
            ReconstruirPorDataHelper helper,
            AuditPublisher auditPublisher) {
        this.lancamentosGateway = lancamentosGateway;
        this.repository         = repository;
        this.helper             = helper;
        this.auditPublisher     = auditPublisher;
    }

    // Sem @Transactional — cada data tem sua própria transação via ReconstruirPorDataHelper
    // (REQUIRES_NEW). Falha em uma data não reverte as anteriores.
    @Override
    public Resultado executar(Command command) {
        var inicio = command.dataInicio();
        var fim    = command.dataFim();

        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException("data_fim não pode ser anterior a data_inicio");
        }
        if (ChronoUnit.DAYS.between(inicio, fim) >= PERIODO_MAXIMO_DIAS) {
            throw new IllegalArgumentException("Período máximo permitido é de 90 dias");
        }

        int datasProcessadas       = 0;
        int divergenciasCorrigidas = 0;

        for (LocalDate data = inicio; !data.isAfter(fim); data = data.plusDays(1)) {
            try {
                var resumo = lancamentosGateway.buscarResumoDiario(data);
                if (resumo.totalLancamentos() == 0) continue;

                var saldoAtual = repository.buscarPorData(data);
                var novoSaldo  = SaldoConsolidado.reconstituir(
                        data,
                        resumo.totalCreditos(),
                        resumo.totalDebitos(),
                        (int) resumo.totalLancamentos(),
                        LocalDateTime.now());

                boolean diverge = saldoAtual.isEmpty()
                        || saldoAtual.get().getTotalCreditos().compareTo(resumo.totalCreditos()) != 0
                        || saldoAtual.get().getTotalDebitos().compareTo(resumo.totalDebitos()) != 0;

                helper.salvarEEvictCache(data, novoSaldo);
                datasProcessadas++;

                if (diverge) {
                    divergenciasCorrigidas++;
                    log.atInfo()
                            .addKeyValue("event", "reconstrucao_corrigido")
                            .addKeyValue("data",  data)
                            .log("Saldo corrigido durante reconstrução");
                }
            } catch (Exception e) {
                log.atWarn()
                        .addKeyValue("event", "reconstrucao_falha_data")
                        .addKeyValue("data",  data)
                        .setCause(e)
                        .log("Falha ao reconstruir data — continuando para a próxima");
            }
        }

        log.atInfo()
                .addKeyValue("event",       "reconstrucao_concluida")
                .addKeyValue("inicio",      inicio)
                .addKeyValue("fim",         fim)
                .addKeyValue("processadas", datasProcessadas)
                .addKeyValue("corrigidas",  divergenciasCorrigidas)
                .log("Reconstrução do consolidado concluída");

        auditPublisher.registrar(new AuditEvento(
                command.operadorId(),
                "consolidado.reconstruido",
                null,
                Map.of(
                        "data_inicio",           inicio.toString(),
                        "data_fim",              fim.toString(),
                        "datas_processadas",     String.valueOf(datasProcessadas),
                        "divergencias_corrigidas", String.valueOf(divergenciasCorrigidas)
                )
        ));

        return new Resultado(datasProcessadas, divergenciasCorrigidas, inicio, fim);
    }
}
