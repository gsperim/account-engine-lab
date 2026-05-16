package br.com.carrefour.lancamentos.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class LancamentoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 5, 9);
    private static final Valor VALOR_150 = Valor.de("150.00");
    private static final String OPERADOR = "usr_abc123";

    @Test
    void deveCriarLancamentoComCamposObrigatorios() {
        var id = LancamentoId.novo();
        var lancamento = Lancamento.criar(id, TipoLancamento.CREDITO, VALOR_150, null, HOJE, OPERADOR);

        assertThat(lancamento.getId()).isEqualTo(id);
        assertThat(lancamento.getTipo()).isEqualTo(TipoLancamento.CREDITO);
        assertThat(lancamento.getValor()).isEqualTo(VALOR_150);
        assertThat(lancamento.getDataCompetencia()).isEqualTo(HOJE);
        assertThat(lancamento.getOperadorId()).isEqualTo(OPERADOR);
        assertThat(lancamento.getCriadoEm()).isNotNull();
    }

    @Test
    void deveCriarLancamentoDebito() {
        var lancamento = Lancamento.criar(LancamentoId.novo(), TipoLancamento.DEBITO, VALOR_150, "Pagamento", HOJE, OPERADOR);
        assertThat(lancamento.getTipo()).isEqualTo(TipoLancamento.DEBITO);
        assertThat(lancamento.getDescricao()).isEqualTo("Pagamento");
    }

    @Test
    void devePreservarIdFornecido() {
        var id1 = LancamentoId.novo();
        var id2 = LancamentoId.novo();
        var l1 = Lancamento.criar(id1, TipoLancamento.CREDITO, VALOR_150, null, HOJE, OPERADOR);
        var l2 = Lancamento.criar(id2, TipoLancamento.CREDITO, VALOR_150, null, HOJE, OPERADOR);
        assertThat(l1.getId()).isNotEqualTo(l2.getId());
    }

    @Test
    void deveLancarExcecaoSemId() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Lancamento.criar(null, TipoLancamento.CREDITO, VALOR_150, null, HOJE, OPERADOR))
            .withMessage("Id é obrigatório");
    }

    @Test
    void deveLancarExcecaoSemTipo() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Lancamento.criar(LancamentoId.novo(), null, VALOR_150, null, HOJE, OPERADOR))
            .withMessage("Tipo é obrigatório");
    }

    @Test
    void deveLancarExcecaoSemValor() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Lancamento.criar(LancamentoId.novo(), TipoLancamento.CREDITO, null, null, HOJE, OPERADOR))
            .withMessage("Valor é obrigatório");
    }

    @Test
    void deveLancarExcecaoSemDataCompetencia() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Lancamento.criar(LancamentoId.novo(), TipoLancamento.CREDITO, VALOR_150, null, null, OPERADOR))
            .withMessage("Data de competência é obrigatória");
    }

    @Test
    void deveLancarExcecaoSemOperadorId() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Lancamento.criar(LancamentoId.novo(), TipoLancamento.CREDITO, VALOR_150, null, HOJE, ""))
            .withMessage("OperadorId é obrigatório");
    }

    @Test
    void deveReconstituirAPartirDaPersistencia() {
        var id = LancamentoId.novo();
        var original = Lancamento.criar(id, TipoLancamento.CREDITO, VALOR_150, "Venda", HOJE, OPERADOR);
        var reconstituido = Lancamento.reconstituir(
            id, TipoLancamento.CREDITO, VALOR_150, "Venda", HOJE, OPERADOR, original.getCriadoEm(), original.getPayloadHash(), false, null
        );
        assertThat(reconstituido.getId()).isEqualTo(id);
        assertThat(reconstituido.getCriadoEm()).isEqualTo(original.getCriadoEm());
    }
}
