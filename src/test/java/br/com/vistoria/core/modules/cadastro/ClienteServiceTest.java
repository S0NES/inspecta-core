package br.com.vistoria.core.modules.cadastro;

import br.com.vistoria.core.modules.cadastro.CadastroRepositories.ClienteRepository;
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
@DisplayName("ClienteService")
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    private ClienteService service;

    @BeforeEach
    void setUp() {
        service = new ClienteService(clienteRepository);
    }

    @Test
    @DisplayName("emailPorId returns email when cliente exists")
    void emailPorId_clienteExists_returnsEmail() {
        Cliente cliente = new Cliente();
        cliente.setId(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        cliente.setEmail("joao@example.com");

        when(clienteRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000100"))).thenReturn(Optional.of(cliente));

        Optional<String> result = service.emailPorId(UUID.fromString("00000000-0000-0000-0000-000000000100"));

        assertThat(result).isPresent().contains("joao@example.com");
        verify(clienteRepository).findById(UUID.fromString("00000000-0000-0000-0000-000000000100"));
    }

    @Test
    @DisplayName("emailPorId returns empty Optional when cliente not found")
    void emailPorId_clienteNotFound_returnsEmpty() {
        when(clienteRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000999"))).thenReturn(Optional.empty());

        Optional<String> result = service.emailPorId(UUID.fromString("00000000-0000-0000-0000-000000000999"));

        assertThat(result).isEmpty();
        verify(clienteRepository).findById(UUID.fromString("00000000-0000-0000-0000-000000000999"));
    }

    @Test
    @DisplayName("emailPorId handles cliente with null email")
    void emailPorId_nullEmail_returnsEmpty() {
        Cliente cliente = new Cliente();
        cliente.setId(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        cliente.setEmail(null);

        when(clienteRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000100"))).thenReturn(Optional.of(cliente));

        Optional<String> result = service.emailPorId(UUID.fromString("00000000-0000-0000-0000-000000000100"));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("emailPorId handles cliente with empty email string")
    void emailPorId_emptyEmail_returnsEmpty() {
        Cliente cliente = new Cliente();
        cliente.setId(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        cliente.setEmail("");

        when(clienteRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000100"))).thenReturn(Optional.of(cliente));

        Optional<String> result = service.emailPorId(UUID.fromString("00000000-0000-0000-0000-000000000100"));

        assertThat(result).contains("");
    }

    @Test
    @DisplayName("emailPorId with different IDs returns correct emails")
    void emailPorId_multipleClientes() {
        Cliente cliente1 = new Cliente();
        cliente1.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        cliente1.setEmail("user1@example.com");

        Cliente cliente2 = new Cliente();
        cliente2.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        cliente2.setEmail("user2@example.com");

        when(clienteRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(Optional.of(cliente1));
        when(clienteRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000002"))).thenReturn(Optional.of(cliente2));

        assertThat(service.emailPorId(UUID.fromString("00000000-0000-0000-0000-000000000001"))).contains("user1@example.com");
        assertThat(service.emailPorId(UUID.fromString("00000000-0000-0000-0000-000000000002"))).contains("user2@example.com");

        verify(clienteRepository).findById(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        verify(clienteRepository).findById(UUID.fromString("00000000-0000-0000-0000-000000000002"));
    }

    @Test
    @DisplayName("emailPorId is read-only transaction (no modifications)")
    void emailPorId_readOnly() {
        Cliente cliente = new Cliente();
        cliente.setId(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        cliente.setEmail("test@example.com");

        when(clienteRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000100"))).thenReturn(Optional.of(cliente));

        service.emailPorId(UUID.fromString("00000000-0000-0000-0000-000000000100"));

        // Should only call findById, not save or delete
        verify(clienteRepository, only()).findById(UUID.fromString("00000000-0000-0000-0000-000000000100"));
    }

    @Test
    @DisplayName("emailPorId with an id that does not exist")
    void emailPorId_unknownId() {
        UUID unknown = UUID.fromString("00000000-0000-0000-0000-000000000000");
        when(clienteRepository.findById(unknown)).thenReturn(Optional.empty());

        Optional<String> result = service.emailPorId(unknown);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("emailPorId extracts email from cliente without modifying it")
    void emailPorId_doesNotModifyCliente() {
        Cliente cliente = new Cliente();
        cliente.setId(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        cliente.setEmail("original@example.com");

        when(clienteRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000100"))).thenReturn(Optional.of(cliente));

        service.emailPorId(UUID.fromString("00000000-0000-0000-0000-000000000100"));

        // Email should remain unchanged
        assertThat(cliente.getEmail()).isEqualTo("original@example.com");
        verify(clienteRepository, never()).save(any());
    }
}
