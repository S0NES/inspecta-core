package br.com.vistoria.core.modules.disponibilidade;

import br.com.vistoria.core.modules.disponibilidade.DisponibilidadeRepositories.VistoriadorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VistoriadorService")
class VistoriadorServiceTest {

    @Mock
    private VistoriadorRepository vistoriadorRepository;

    private VistoriadorService service;

    @BeforeEach
    void setUp() {
        service = new VistoriadorService(vistoriadorRepository);
    }

    @Test
    @DisplayName("idPorUsuario returns vistoriador ID when exists")
    void idPorUsuario_vistoriadorExists_returnsId() {
        Vistoriador vistoriador = new Vistoriador();
        vistoriador.setId(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        vistoriador.setUsuarioId(UUID.fromString("00000000-0000-0000-0000-000000000050"));

        when(vistoriadorRepository.findByUsuarioId(UUID.fromString("00000000-0000-0000-0000-000000000050"))).thenReturn(Optional.of(vistoriador));

        Optional<UUID> result = service.idPorUsuario(UUID.fromString("00000000-0000-0000-0000-000000000050"));

        assertThat(result).isPresent().contains(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        verify(vistoriadorRepository).findByUsuarioId(UUID.fromString("00000000-0000-0000-0000-000000000050"));
    }

    @Test
    @DisplayName("idPorUsuario returns empty Optional when vistoriador not found")
    void idPorUsuario_vistoriadorNotFound_returnsEmpty() {
        when(vistoriadorRepository.findByUsuarioId(UUID.fromString("00000000-0000-0000-0000-000000000999"))).thenReturn(Optional.empty());

        Optional<UUID> result = service.idPorUsuario(UUID.fromString("00000000-0000-0000-0000-000000000999"));

        assertThat(result).isEmpty();
        verify(vistoriadorRepository).findByUsuarioId(UUID.fromString("00000000-0000-0000-0000-000000000999"));
    }

    @Test
    @DisplayName("idPorUsuario extracts correct ID from vistoriador")
    void idPorUsuario_extractsCorrectId() {
        Vistoriador vistoriador = new Vistoriador();
        vistoriador.setId(UUID.fromString("00000000-0000-0000-0000-000000000555"));
        vistoriador.setUsuarioId(UUID.fromString("00000000-0000-0000-0000-000000000100"));

        when(vistoriadorRepository.findByUsuarioId(UUID.fromString("00000000-0000-0000-0000-000000000100"))).thenReturn(Optional.of(vistoriador));

        Optional<UUID> result = service.idPorUsuario(UUID.fromString("00000000-0000-0000-0000-000000000100"));

        assertThat(result).contains(UUID.fromString("00000000-0000-0000-0000-000000000555"));
    }

    @Test
    @DisplayName("idPorUsuario with different usuarios returns correct IDs")
    void idPorUsuario_multipleUsuarios() {
        Vistoriador vistoriador1 = new Vistoriador();
        vistoriador1.setId(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        vistoriador1.setUsuarioId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        Vistoriador vistoriador2 = new Vistoriador();
        vistoriador2.setId(UUID.fromString("00000000-0000-0000-0000-000000000020"));
        vistoriador2.setUsuarioId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

        when(vistoriadorRepository.findByUsuarioId(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(Optional.of(vistoriador1));
        when(vistoriadorRepository.findByUsuarioId(UUID.fromString("00000000-0000-0000-0000-000000000002"))).thenReturn(Optional.of(vistoriador2));

        assertThat(service.idPorUsuario(UUID.fromString("00000000-0000-0000-0000-000000000001"))).contains(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        assertThat(service.idPorUsuario(UUID.fromString("00000000-0000-0000-0000-000000000002"))).contains(UUID.fromString("00000000-0000-0000-0000-000000000020"));

        verify(vistoriadorRepository).findByUsuarioId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        verify(vistoriadorRepository).findByUsuarioId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
    }

    @Test
    @DisplayName("idPorUsuario is read-only (no modifications)")
    void idPorUsuario_readOnly() {
        Vistoriador vistoriador = new Vistoriador();
        vistoriador.setId(UUID.fromString("00000000-0000-0000-0000-000000000030"));
        vistoriador.setUsuarioId(UUID.fromString("00000000-0000-0000-0000-000000000005"));

        when(vistoriadorRepository.findByUsuarioId(UUID.fromString("00000000-0000-0000-0000-000000000005"))).thenReturn(Optional.of(vistoriador));

        service.idPorUsuario(UUID.fromString("00000000-0000-0000-0000-000000000005"));

        // Should only call findByUsuarioId, not save or delete
        verify(vistoriadorRepository, only()).findByUsuarioId(UUID.fromString("00000000-0000-0000-0000-000000000005"));
    }

    @Test
    @DisplayName("idPorUsuario with an usuarioId that has no vistoriador")
    void idPorUsuario_unknownId() {
        UUID unknown = UUID.fromString("00000000-0000-0000-0000-000000000000");
        when(vistoriadorRepository.findByUsuarioId(unknown)).thenReturn(Optional.empty());

        Optional<UUID> result = service.idPorUsuario(unknown);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("idPorUsuario is deterministic (same input produces same output)")
    void idPorUsuario_deterministic() {
        Vistoriador vistoriador = new Vistoriador();
        vistoriador.setId(UUID.fromString("00000000-0000-0000-0000-000000000077"));
        vistoriador.setUsuarioId(UUID.fromString("00000000-0000-0000-0000-000000000033"));

        when(vistoriadorRepository.findByUsuarioId(UUID.fromString("00000000-0000-0000-0000-000000000033"))).thenReturn(Optional.of(vistoriador));

        Optional<UUID> result1 = service.idPorUsuario(UUID.fromString("00000000-0000-0000-0000-000000000033"));
        Optional<UUID> result2 = service.idPorUsuario(UUID.fromString("00000000-0000-0000-0000-000000000033"));

        assertThat(result1).isEqualTo(result2);
        assertThat(result1).contains(UUID.fromString("00000000-0000-0000-0000-000000000077"));
    }

    @Test
    @DisplayName("idPorUsuario does not modify vistoriador")
    void idPorUsuario_doesNotModifyVistoriador() {
        Vistoriador vistoriador = new Vistoriador();
        vistoriador.setId(UUID.fromString("00000000-0000-0000-0000-000000000040"));
        vistoriador.setUsuarioId(UUID.fromString("00000000-0000-0000-0000-000000000010"));

        when(vistoriadorRepository.findByUsuarioId(UUID.fromString("00000000-0000-0000-0000-000000000010"))).thenReturn(Optional.of(vistoriador));

        service.idPorUsuario(UUID.fromString("00000000-0000-0000-0000-000000000010"));

        // Vistoriador should remain unchanged
        assertThat(vistoriador.getId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000040"));
        verify(vistoriadorRepository, never()).save(any());
    }

}
