package br.com.vistoria.core.modules.financeiro;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * V4 (D3): um item de extrato = o preço snapshot de um {@code solicitacao_servico} B2B
 * no momento da criação da solicitação. {@code faturaId == null} enquanto está em aberto;
 * a cobrança consolidada preenche {@code faturaId} ao gerar a {@link FaturaConsolidada}.
 */
@Entity @Table(name = "extrato_itens")
@Getter @Setter @NoArgsConstructor
public class ExtratoItem {
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false) private UUID imobiliariaId;
    @Column(nullable = false) private UUID solicitacaoServicoId;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal valor;
    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();
    @Column(name = "fatura_id") private UUID faturaId;
}
