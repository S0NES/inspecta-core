package br.com.vistoria.core.modules.biletagemcliente;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BiletagemClienteRepositories {
    interface BiletagemClienteRepository extends JpaRepository<BiletagemCliente, Boolean> {}

    interface BiletagemClientePercentualRepository
            extends JpaRepository<BiletagemClientePercentual, UUID> {}

    interface BiletagemClienteFaixaRepository extends JpaRepository<BiletagemClienteFaixa, UUID> {
        List<BiletagemClienteFaixa> findByB2cServiceId(UUID b2cServiceId);
        Optional<BiletagemClienteFaixa> findByB2cServiceIdAndFaixaReferenciaId(
                UUID b2cServiceId, Short faixaReferenciaId);
    }
}
