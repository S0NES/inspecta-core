package br.com.vistoria.core.modules.usuario;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Vínculo muitos-para-muitos usuário ↔ imobiliária (V25).
 *
 * <p>Um corretor/vistoriador pode atender várias imobiliárias. A RLS (V26) filtra
 * por qualquer vínculo do usuário. `usuarios.imobiliaria_id` continua sendo a
 * imobiliária primária/ativa.</p>
 */
@Entity
@Table(name = "usuario_imobiliarias")
@IdClass(UsuarioImobiliaria.Pk.class)
@Getter @Setter @NoArgsConstructor
public class UsuarioImobiliaria {

    @Id private UUID usuarioId;
    @Id private UUID imobiliariaId;

    public UsuarioImobiliaria(UUID usuarioId, UUID imobiliariaId) {
        this.usuarioId = usuarioId;
        this.imobiliariaId = imobiliariaId;
    }

    @Getter @Setter @NoArgsConstructor
    public static class Pk implements Serializable {
        private UUID usuarioId;
        private UUID imobiliariaId;

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(usuarioId, pk.usuarioId) && Objects.equals(imobiliariaId, pk.imobiliariaId);
        }
        @Override public int hashCode() { return Objects.hash(usuarioId, imobiliariaId); }
    }
}
