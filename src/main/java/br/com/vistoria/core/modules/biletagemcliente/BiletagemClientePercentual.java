package br.com.vistoria.core.modules.biletagemcliente;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "biletagem_cliente_percentual")
@Getter @Setter @NoArgsConstructor
public class BiletagemClientePercentual {

    @Id
    @Column(name = "b2c_service_id")
    private UUID b2cServiceId;

    @Column(nullable = false)
    private BigDecimal percentual;

    private BigDecimal minimo;
}
