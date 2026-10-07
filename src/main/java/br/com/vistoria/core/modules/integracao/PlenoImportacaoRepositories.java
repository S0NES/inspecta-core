package br.com.vistoria.core.modules.integracao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlenoImportacaoRepositories {

    interface PlenoImportacaoVistoriaRepository extends JpaRepository<PlenoImportacaoVistoria, UUID> {

        Optional<PlenoImportacaoVistoria> findBySolicitacaoServicoId(UUID solicitacaoServicoId);

        List<PlenoImportacaoVistoria> findByStatusImportacao(String statusImportacao);

        /** Elegíveis para o poller: acompanhamento ainda em curso e próxima verificação já vencida. */
        List<PlenoImportacaoVistoria> findByStatusImportacaoInAndProximaVerificacaoEmLessThanEqual(
                List<String> statusImportacao, Instant limite);
    }

    interface PlenoImportacaoCheckpointRepository extends JpaRepository<PlenoImportacaoCheckpoint, Short> {
    }
}
