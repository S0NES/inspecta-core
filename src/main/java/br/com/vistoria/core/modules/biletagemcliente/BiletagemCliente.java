package br.com.vistoria.core.modules.biletagemcliente;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Config global de biletagem B2C — linha única (id fixo TRUE, ver V13). */
@Entity
@Table(name = "biletagem_cliente")
@Getter @Setter @NoArgsConstructor
public class BiletagemCliente {

    public enum Modo { percentual, faixa }

    @Id
    private Boolean id = Boolean.TRUE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Modo modo;
}
