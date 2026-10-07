package br.com.vistoria.core.modules.usuario;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usuarios")
@Getter @Setter @NoArgsConstructor
public class Usuario {

    public enum Perfil { admin, suporte, imob_gerencia, imob_operacao, vistoriador, cliente }
    public enum Status { convidado, ativo, inativo }
    public enum Tema { light, dark }

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    private UUID imobiliariaId;
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Perfil perfil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.convidado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tema tema = Tema.light;

    @Column(nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();
}
