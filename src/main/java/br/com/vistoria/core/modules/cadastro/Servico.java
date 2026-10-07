package br.com.vistoria.core.modules.cadastro;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

/**
 * V3 — "Serviço" guarda-chuva (ex.: "Elegance") que agrupa N {@link TipoServico}
 * (ex.: "Vistoria de entrada", "Vistoria de saída"). Catálogo global; só admin/suporte
 * criam/editam (RBAC no inspecta-api). O vínculo M:N vive em {@link ServicoTipo}.
 */
@Entity @Table(name = "servicos")
@Getter @Setter @NoArgsConstructor
public class Servico {
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME) private UUID id;
    @Column(nullable = false, unique = true) private String nome;
    @Column(nullable = false) private boolean ativo = true;
    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();
}
