package br.com.carrefour.consolidado.application.usecase;

import br.com.carrefour.consolidado.domain.model.SaldoConsolidado;
import br.com.carrefour.consolidado.domain.port.in.ReconstruirConsolidadoUseCase;
import br.com.carrefour.consolidado.domain.port.out.LancamentosGateway;
import br.com.carrefour.consolidado.domain.port.out.SaldoConsolidadoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class ReconstruirConsolidadoService implements ReconstruirConsolidadoUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReconstruirConsolidadoService.class);
    private static final int PERIODO_MAXIMO_DIAS = 90;

    private final LancamentosGateway lancamentosGateway;
    private final SaldoConsolidadoRepository repository;

    public ReconstruirConsolidadoService(LancamentosGateway lancamentosGateway,
                                          SaldoConsolidadoRepository repository) {
        this.lancamentosGateway = lancamentosGateway;
        this.repository = repository;
    }

    @Override
    @Transactional
    public Resultado executar(Command command) {
        var inicio = command.dataInicio();
        var fim    = command.dataFim();

        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException("data_fim não pode ser anterior a data_inicio");
        }
        if (ChronoUnit.DAYS.between(inicio, fim) >= PERIODO_MAXIMO_DIAS) {
            throw new IllegalArgumentException("Período máximo permitido é de 90 dias");
        }

        int datasProcessadas    = 0;
        int divergenciasCorrigidas = 0;

        for (LocalDate data = inicio; !data.isAfter(fim); data = data.plusDays(1)) {
            var resumo = lancamentosGateway.buscarResumoDiario(data);

            if (resumo.totalLancamentos() == 0) continue;

            var saldoAtual = repository.buscarPorData(data);
            var novoSaldo  = SaldoConsolidado.reconstituir(
                    data,
                    resumo.totalCreditos(),
                    resumo.totalDebitos(),
                    (int) resumo.totalLancamentos(),
                    java.time.LocalDateTime.now());

            boolean diverge = saldoAtual.isEmpty()
                    || saldoAtual.get().getTotalCreditos().compareTo(resumo.totalCreditos()) != 0
                    || saldoAtual.get().getTotalDebitos().compareTo(resumo.totalDebitos()) != 0;

            evictCache(data);
            repository.salvar(novoSaldo);
            datasProcessadas++;

            if (diverge) {
                divergenciasCorrigidas++;
                log.atInfo()
                        .addKeyValue("event", "reconstrucao_corrigido")
                        .addKeyValue("data",  data)
                        .log("Saldo corrigido durante reconstrução");
            }
        }

        log.atInfo()
                .addKeyValue("event",      "reconstrucao_concluida")
                .addKeyValue("inicio",     inicio)
                .addKeyValue("fim",        fim)
                .addKeyValue("processadas", datasProcessadas)
                .addKeyValue("corrigidas",  divergenciasCorrigidas)
                .log("Reconstrução do consolidado concluída");

        return new Resultado(datasProcessadas, divergenciasCorrigidas, inicio, fim);
    }

    @CacheEvict(value = "saldo-consolidado", key = "#data")
    public void evictCache(LocalDate data) {}
}
