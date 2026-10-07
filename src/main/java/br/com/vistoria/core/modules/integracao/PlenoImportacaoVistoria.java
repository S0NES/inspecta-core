package br.com.vistoria.core.modules.integracao;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * V3: estado operacional do acompanhamento de UMA vistoria já espelhada na Pleno,
 * da solicitação da vistoria até o PDF baixado e enviado para assinatura. O ID
 * externo (sol_codigo) NÃO é repetido aqui — mora em {@code integracao_sync}
 * ({@code recurso_tipo='solicitacao'}); esta tabela só guarda o que é específico
 * do acompanhamento (backoff, tentativas, link/hash do PDF, flags de idempotência).
 *
 * <p>Sem RLS de tenant — tabela operacional (equipe Inspecta/inspecta-worker), padrão
 * {@code integracao_sync}. {@code status} guardado como String, enum só para os nomes.</p>
 */
@Entity @Table(name = "pleno_importacao_vistoria")
@Getter @Setter @NoArgsConstructor
public class PlenoImportacaoVistoria {

    public enum Status {
        SOLICITADA, EM_ANDAMENTO, FINALIZADA_SEM_PDF, FINALIZADA_COM_PDF,
        PDF_BAIXADO, ENVIADA_PARA_ASSINATURA, CANCELADA, ERRO
    }

    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false) private UUID solicitacaoServicoId;
    private Integer staSolCodigoAtual;
    @Column(name = "status_importacao", nullable = false, length = 30)
    private String statusImportacao = Status.SOLICITADA.name();
    @Column(nullable = false) private int tentativasVerificacao = 0;
    private Instant ultimaVerificacaoEm;
    @Column(nullable = false) private Instant proximaVerificacaoEm = Instant.now();
    private String linkPdfExterno;
    private UUID pdfArquivoId;
    @Column(length = 64) private String pdfSha256;
    private UUID laudoId;
    private Instant enviadoParaAssinaturaEm;
    private String erroMensagem;
    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();
    @Column(nullable = false) private Instant atualizadoEm = Instant.now();
}
