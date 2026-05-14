package br.com.carrefour.lancamentos.application.usecase;

import br.com.carrefour.lancamentos.domain.model.Lancamento;
import br.com.carrefour.lancamentos.domain.model.LancamentoId;
import br.com.carrefour.lancamentos.domain.port.in.BuscarLancamentoUseCase;
import br.com.carrefour.lancamentos.domain.port.out.LancamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class BuscarLancamentoService implements BuscarLancamentoUseCase {

    private final LancamentoRepository repository;

    public BuscarLancamentoService(LancamentoRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Lancamento> executar(LancamentoId id) {
        return repository.buscarPorId(id);
    }
}
