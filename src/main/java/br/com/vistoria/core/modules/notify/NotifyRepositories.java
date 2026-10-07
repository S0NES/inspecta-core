package br.com.vistoria.core.modules.notify;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotifyRepositories {
    interface MessageTemplateRepository extends JpaRepository<MessageTemplate, MessageTemplate.Pk> {
        /** Resolução em runtime: maior versão ativa de um template. */
        Optional<MessageTemplate> findFirstByUuidAndAtivoTrueOrderByVersaoDesc(UUID uuid);

        /** Maior versão já existente (ativa ou não), para calcular a próxima. */
        Optional<MessageTemplate> findFirstByUuidOrderByVersaoDesc(UUID uuid);

        /** Histórico completo de um template. */
        List<MessageTemplate> findByUuidOrderByVersaoDesc(UUID uuid);

        /**
         * Uma linha por template (a maior versão), opcionalmente filtrado por canal.
         *
         * <p>{@code cast(:canal as string)} é necessário: sem o cast, o PostgreSQL não consegue
         * inferir o tipo do bind em {@code :canal is null} e estoura 42P18 ("could not determine
         * data type of parameter $1") quando {@code canal} vem nulo — {@code GET /v1/notify/templates}
         * sem filtro (RELATORIO.md achado #3).</p>
         */
        @Query("""
                select t from MessageTemplate t
                where t.versao = (select max(t2.versao) from MessageTemplate t2 where t2.uuid = t.uuid)
                  and (cast(:canal as string) is null or t.canal = :canal)
                order by t.nome asc
                """)
        List<MessageTemplate> listarUltimaVersao(@Param("canal") String canal);
    }
    interface EntregaRepository extends JpaRepository<Entrega, UUID> {
        Optional<Entrega> findByIdempotencyKey(String idempotencyKey);
        Optional<Entrega> findFirstByExternalIdOrderByIdDesc(String externalId);
    }
    interface WebhookEventoRepository extends JpaRepository<NotifyWebhookEvento, UUID> {}

    /** Ligação utilidade -> template (uma linha por utilidade, id = TemplateUtilidade.name()). */
    interface TemplateUtilidadeRepository extends JpaRepository<TemplateUtilidadeEntity, String> {}
}
