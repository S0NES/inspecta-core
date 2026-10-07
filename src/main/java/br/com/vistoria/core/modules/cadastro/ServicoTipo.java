package br.com.vistoria.core.modules.cadastro;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.util.UUID;

/**
 * V3 — vínculo M:N entre {@link Servico} guarda-chuva e {@link TipoServico}.
 * Um Tipo de Serviço pode ser reaproveitado em mais de um Serviço.
 *
 * V5 — {@code plenoTipVisCodigo}: código Pleno (tip_vis_codigo) desta combinação
 * Serviço × Tipo de Serviço. Nullable: sem código configurado, cai no de-para/default
 * legado (resolvido no inspecta-worker).
 */
@Entity @Table(name = "servico_tipos")
@Getter @Setter @NoArgsConstructor
public class ServicoTipo {
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false) private UUID servicoId;
    @Column(nullable = false) private UUID tipoServicoId;
    @Column(name = "pleno_tip_vis_codigo") private Integer plenoTipVisCodigo;
}
