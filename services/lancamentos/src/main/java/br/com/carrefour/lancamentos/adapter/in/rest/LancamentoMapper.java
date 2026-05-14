package br.com.carrefour.lancamentos.adapter.in.rest;

import br.com.carrefour.lancamentos.adapter.in.rest.dto.generated.LancamentoResponse;
import br.com.carrefour.lancamentos.adapter.in.rest.dto.generated.PageLancamentoResponse;
import br.com.carrefour.lancamentos.domain.model.Lancamento;
import br.com.carrefour.lancamentos.domain.model.TipoLancamento;
import br.com.carrefour.lancamentos.domain.port.in.ListarLancamentosUseCase;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;

@Component
public class LancamentoMapper {

    public TipoLancamento toDomain(
            br.com.carrefour.lancamentos.adapter.in.rest.dto.generated.TipoLancamento tipo) {
        if (tipo == null) return null;
        return TipoLancamento.valueOf(tipo.name());
    }

    public br.com.carrefour.lancamentos.adapter.in.rest.dto.generated.TipoLancamento toDto(
            TipoLancamento tipo) {
        if (tipo == null) return null;
        return br.com.carrefour.lancamentos.adapter.in.rest.dto.generated.TipoLancamento.valueOf(tipo.name());
    }

    public LancamentoResponse toResponse(Lancamento l) {
        return new LancamentoResponse()
                .id(l.getId().toUUID())
                .tipo(toDto(l.getTipo()))
                .valor(l.getValor().toBigDecimal().doubleValue())
                .descricao(l.getDescricao())
                .dataCompetencia(l.getDataCompetencia())
                .criadoEm(l.getCriadoEm().atOffset(ZoneOffset.UTC))
                .operadorId(l.getOperadorId());
    }

    public PageLancamentoResponse toPageResponse(ListarLancamentosUseCase.Result result) {
        var content = result.items().stream().map(this::toResponse).toList();
        return new PageLancamentoResponse()
                .content(content)
                .page(result.page())
                .size(result.size())
                .totalElements((int) result.total())
                .totalPages(result.totalPages());
    }
}
