package br.com.vistoria.core.modules.cadastro;

import br.com.vistoria.core.modules.cadastro.CadastroRepositories.ImovelRepository;
import br.com.vistoria.core.modules.integracao.IntegracaoSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import org.mockito.InOrder;

@ExtendWith(MockitoExtension.class)
@DisplayName("CadastroService")
class CadastroServiceTest {

    @Mock
    private ImovelRepository imovelRepository;

    @Mock
    private IntegracaoSyncService integracaoSync;

    private CadastroService service;

    @BeforeEach
    void setUp() {
        service = new CadastroService(imovelRepository, integracaoSync);
    }

    @Test
    @DisplayName("criarImovel saves imovel and enqueues sync")
    void criarImovel_savesAndEnqueuesSync() {
        Imovel imovel = new Imovel();
        imovel.setEndereco("Rua A, 123");

        Imovel savedImovel = new Imovel();
        savedImovel.setId(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        savedImovel.setEndereco("Rua A, 123");

        when(imovelRepository.save(imovel)).thenReturn(savedImovel);

        Imovel result = service.criarImovel(imovel);

        assertThat(result).isEqualTo(savedImovel);
        assertThat(result.getId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000100"));

        verify(imovelRepository).save(imovel);
        verify(integracaoSync).enfileirar("imovel", UUID.fromString("00000000-0000-0000-0000-000000000100"));
    }

    @Test
    @DisplayName("criarImovel enqueues with correct imovel ID")
    void criarImovel_enqueuesWithCorrectId() {
        Imovel imovel = new Imovel();
        imovel.setEndereco("Rua B, 456");

        Imovel savedImovel = new Imovel();
        savedImovel.setId(UUID.fromString("00000000-0000-0000-0000-000000000999"));
        savedImovel.setEndereco("Rua B, 456");

        when(imovelRepository.save(imovel)).thenReturn(savedImovel);

        service.criarImovel(imovel);

        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(integracaoSync).enfileirar(eq("imovel"), idCaptor.capture());

        assertThat(idCaptor.getValue()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000999"));
    }

    @Test
    @DisplayName("criarImovel returns saved imovel")
    void criarImovel_returnsSavedImovel() {
        Imovel imovel = new Imovel();
        imovel.setEndereco("Rua C, 789");

        Imovel savedImovel = new Imovel();
        savedImovel.setId(UUID.fromString("00000000-0000-0000-0000-000000000050"));
        savedImovel.setEndereco("Rua C, 789");

        when(imovelRepository.save(imovel)).thenReturn(savedImovel);

        Imovel result = service.criarImovel(imovel);

        assertThat(result).isSameAs(savedImovel);
    }

    @Test
    @DisplayName("criarImovel enqueues even if sync fails (transactional)")
    void criarImovel_enqueuesBeforeSyncFailure() {
        Imovel imovel = new Imovel();
        imovel.setEndereco("Rua D, 101");

        Imovel savedImovel = new Imovel();
        savedImovel.setId(UUID.fromString("00000000-0000-0000-0000-000000000075"));
        savedImovel.setEndereco("Rua D, 101");

        when(imovelRepository.save(imovel)).thenReturn(savedImovel);
        doThrow(new RuntimeException("Sync failed")).when(integracaoSync).enfileirar("imovel", UUID.fromString("00000000-0000-0000-0000-000000000075"));

        // The service should still enqueue even if an exception is thrown
        try {
            service.criarImovel(imovel);
        } catch (RuntimeException e) {
            // Expected
        }

        verify(imovelRepository).save(imovel);
        verify(integracaoSync).enfileirar("imovel", UUID.fromString("00000000-0000-0000-0000-000000000075"));
    }

    @Test
    @DisplayName("criarImovel saves before enqueueing")
    void criarImovel_saveBeforeEnqueue() {
        Imovel imovel = new Imovel();
        imovel.setEndereco("Rua E, 202");

        Imovel savedImovel = new Imovel();
        savedImovel.setId(UUID.fromString("00000000-0000-0000-0000-000000000080"));
        savedImovel.setEndereco("Rua E, 202");

        when(imovelRepository.save(imovel)).thenReturn(savedImovel);

        service.criarImovel(imovel);

        // Verify order: save is called before enqueue
        InOrder inOrder = inOrder(imovelRepository, integracaoSync);
        inOrder.verify(imovelRepository).save(imovel);
        inOrder.verify(integracaoSync).enfileirar("imovel", UUID.fromString("00000000-0000-0000-0000-000000000080"));
    }

    @Test
    @DisplayName("criarImovel with null imovel throws exception")
    void criarImovel_nullImovel() {
        when(imovelRepository.save(null)).thenThrow(new IllegalArgumentException());

        try {
            service.criarImovel(null);
        } catch (Exception e) {
            // Expected
        }

        verify(integracaoSync, never()).enfileirar(anyString(), any(UUID.class));
    }

    @Test
    @DisplayName("criarImovel handles multiple sequential calls")
    void criarImovel_multipleSequentialCalls() {
        Imovel imovel1 = new Imovel();
        imovel1.setEndereco("Rua 1");

        Imovel saved1 = new Imovel();
        saved1.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        saved1.setEndereco("Rua 1");

        Imovel imovel2 = new Imovel();
        imovel2.setEndereco("Rua 2");

        Imovel saved2 = new Imovel();
        saved2.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        saved2.setEndereco("Rua 2");

        when(imovelRepository.save(imovel1)).thenReturn(saved1);
        when(imovelRepository.save(imovel2)).thenReturn(saved2);

        Imovel result1 = service.criarImovel(imovel1);
        Imovel result2 = service.criarImovel(imovel2);

        assertThat(result1.getId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(result2.getId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000002"));

        verify(imovelRepository, times(2)).save(any());
        verify(integracaoSync).enfileirar("imovel", UUID.fromString("00000000-0000-0000-0000-000000000001"));
        verify(integracaoSync).enfileirar("imovel", UUID.fromString("00000000-0000-0000-0000-000000000002"));
    }

    @Test
    @DisplayName("criarImovel preserves imovel data after save")
    void criarImovel_preservesData() {
        Imovel imovel = new Imovel();
        imovel.setEndereco("Rua Test, 999");

        Imovel savedImovel = new Imovel();
        savedImovel.setId(UUID.fromString("00000000-0000-0000-0000-000000000200"));
        savedImovel.setEndereco("Rua Test, 999");

        when(imovelRepository.save(imovel)).thenReturn(savedImovel);

        Imovel result = service.criarImovel(imovel);

        assertThat(result.getId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000200"));
        assertThat(result.getEndereco()).isEqualTo("Rua Test, 999");
    }

    @Test
    @DisplayName("criarImovel preserves numero/complemento/cib/observacoes when informed")
    void criarImovel_preservesEnderecoDetalhado() {
        Imovel imovel = new Imovel();
        imovel.setEndereco("Rua Test");
        imovel.setNumero("123");
        imovel.setComplemento("Apto 45");
        imovel.setCib("CIB-000999");
        imovel.setObservacoes("Portão dos fundos emperra, usar entrada principal.");

        Imovel savedImovel = new Imovel();
        savedImovel.setId(UUID.fromString("00000000-0000-0000-0000-000000000201"));
        savedImovel.setEndereco("Rua Test");
        savedImovel.setNumero("123");
        savedImovel.setComplemento("Apto 45");
        savedImovel.setCib("CIB-000999");
        savedImovel.setObservacoes("Portão dos fundos emperra, usar entrada principal.");

        when(imovelRepository.save(imovel)).thenReturn(savedImovel);

        Imovel result = service.criarImovel(imovel);

        assertThat(result.getNumero()).isEqualTo("123");
        assertThat(result.getComplemento()).isEqualTo("Apto 45");
        assertThat(result.getCib()).isEqualTo("CIB-000999");
        assertThat(result.getObservacoes()).isEqualTo("Portão dos fundos emperra, usar entrada principal.");
    }

    @Test
    @DisplayName("criarImovel accepts numero/complemento/cib/observacoes left null (optional fields)")
    void criarImovel_enderecoDetalhadoIsOptional() {
        Imovel imovel = new Imovel();
        imovel.setEndereco("Rua Rural s/n");

        Imovel savedImovel = new Imovel();
        savedImovel.setId(UUID.fromString("00000000-0000-0000-0000-000000000202"));
        savedImovel.setEndereco("Rua Rural s/n");

        when(imovelRepository.save(imovel)).thenReturn(savedImovel);

        Imovel result = service.criarImovel(imovel);

        assertThat(result.getNumero()).isNull();
        assertThat(result.getComplemento()).isNull();
        assertThat(result.getCib()).isNull();
        assertThat(result.getObservacoes()).isNull();
    }

    @Test
    @DisplayName("criarImovel preserves quartos e cobertura")
    void criarImovel_preservesQuartosECobertura() {
        Imovel imovel = new Imovel();
        imovel.setEndereco("Rua Test");
        imovel.setQuartos(2);
        imovel.setCobertura(true);

        Imovel savedImovel = new Imovel();
        savedImovel.setId(UUID.fromString("00000000-0000-0000-0000-000000000203"));
        savedImovel.setEndereco("Rua Test");
        savedImovel.setQuartos(2);
        savedImovel.setCobertura(true);

        when(imovelRepository.save(imovel)).thenReturn(savedImovel);

        Imovel result = service.criarImovel(imovel);

        assertThat(result.getQuartos()).isEqualTo(2);
        assertThat(result.isCobertura()).isTrue();
    }
}
