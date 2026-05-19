package br.com.carrefour.consolidado.application.usecase;

import br.com.carrefour.consolidado.domain.model.SaldoConsolidado;
import br.com.carrefour.consolidado.domain.port.out.SaldoConsolidadoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReconstruirPorDataHelperTest {

    @Mock SaldoConsolidadoRepository repository;
    @InjectMocks ReconstruirPorDataHelper helper;

    @Test
    void salvarEEvictCache_deveDelegarAoRepositorio() {
        var data  = LocalDate.of(2026, 5, 9);
        var saldo = SaldoConsolidado.reconstituir(data, new BigDecimal("300.00"), new BigDecimal("100.00"),
                3, LocalDateTime.now());

        helper.salvarEEvictCache(data, saldo);

        verify(repository).salvar(saldo);
    }
}
