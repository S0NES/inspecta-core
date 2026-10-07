package br.com.vistoria.core.modules.biletagemcliente;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "biletagem_cliente_faixas")
@Getter @Setter @NoArgsConstructor
public class BiletagemClienteFaixa {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "b2c_service_id", nullable = false)
    private UUID b2cServiceId;

    @Column(nullable = false)
    private Short faixaReferenciaId;

    @Column(nullable = false)
    private BigDecimal valorSemMob;

    @Column(nullable = false)
    private BigDecimal valorComMob;
}
