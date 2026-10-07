package br.com.vistoria.core.modules.integracao;

import br.com.vistoria.core.events.EventTypes;
import br.com.vistoria.core.shared.exception.NotFoundException;
import br.com.vistoria.core.shared.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IntegracaoSyncService")
class IntegracaoSyncServiceTest {

    @Mock
    private IntegracaoSyncRepository repository;

    @Mock
    private OutboxService outbox;

    private IntegracaoSyncService service;

    @BeforeEach
    void setUp() {
        service = new IntegracaoSyncService(repository, outbox);
    }

    @Test
    @DisplayName("retentar volta linha em erro para pendente e re-publica")
    void retentar_reenfileiraErro() {
        IntegracaoSync row = new IntegracaoSync();
        row.setId(UUID.fromString("00000000-0000-0000-0000-000000000009"));
        row.setRecursoTipo("imovel");
        row.setRecursoId(UUID.fromString("00000000-0000-0000-0000-000000000042"));
        row.setStatus(IntegracaoSync.Status.erro.name());
        row.setErro("timeout");
        when(repository.findById(UUID.fromString("00000000-0000-0000-0000-000000000009"))).thenReturn(Optional.of(row));
        when(repository.save(any(IntegracaoSync.class))).thenAnswer(inv -> inv.getArgument(0));

        service.retentar(UUID.fromString("00000000-0000-0000-0000-000000000009"));

        assertThat(row.getStatus()).isEqualTo(IntegracaoSync.Status.pendente.name());
        assertThat(row.getErro()).isNull();
        verify(outbox).publish(eq(EventTypes.PLENO_SYNC_REQUESTED), any());
    }

    @Test
    @DisplayName("retentar mantém status sincronizado (re-publica sync_status, não duplica cadastro)")
    void retentar_sincronizadoMantemStatus() {
        IntegracaoSync row = new IntegracaoSync();
        row.setId(UUID.fromString("00000000-0000-0000-0000-000000000005"));
        row.setRecursoTipo("imobiliaria");
        row.setRecursoId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        row.setExternalId("777");
        row.setStatus(IntegracaoSync.Status.sincronizado.name());
        when(repository.findById(UUID.fromString("00000000-0000-0000-0000-000000000005"))).thenReturn(Optional.of(row));
        when(repository.save(any(IntegracaoSync.class))).thenAnswer(inv -> inv.getArgument(0));

        service.retentar(UUID.fromString("00000000-0000-0000-0000-000000000005"));

        assertThat(row.getStatus()).isEqualTo(IntegracaoSync.Status.sincronizado.name());
        verify(outbox).publish(eq(EventTypes.PLENO_SYNC_REQUESTED), any());
    }

    @Test
    @DisplayName("retentar 404 quando id não existe")
    void retentar_notFound() {
        when(repository.findById(UUID.fromString("00000000-0000-0000-0000-000000000999"))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.retentar(UUID.fromString("00000000-0000-0000-0000-000000000999"))).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("retentar em linha já pendente NÃO re-publica (evita duplicar cadastro em clique repetido)")
    void retentar_pendenteNaoRepublica() {
        IntegracaoSync row = new IntegracaoSync();
        row.setId(UUID.fromString("00000000-0000-0000-0000-000000000008"));
        row.setRecursoTipo("solicitacao");
        row.setRecursoId(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        row.setStatus(IntegracaoSync.Status.pendente.name());
        when(repository.findById(UUID.fromString("00000000-0000-0000-0000-000000000008"))).thenReturn(Optional.of(row));

        service.retentar(UUID.fromString("00000000-0000-0000-0000-000000000008"));

        verify(outbox, never()).publish(any(), any());
        verify(repository, never()).save(any(IntegracaoSync.class));
    }

    @Test
    @DisplayName("enfileirar creates new sync when not exists")
    void enfileirar_createsNew() {
        when(repository.findBySistemaAndRecursoTipoAndRecursoId("pleno", "imovel", UUID.fromString("00000000-0000-0000-0000-000000000100")))
            .thenReturn(Optional.empty());

        when(repository.save(any(IntegracaoSync.class))).thenAnswer(inv -> {
            IntegracaoSync arg = inv.getArgument(0);
            arg.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
            return arg;
        });

        IntegracaoSync result = service.enfileirar("imovel", UUID.fromString("00000000-0000-0000-0000-000000000100"));

        assertThat(result.getId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(result.getRecursoTipo()).isEqualTo("imovel");
        assertThat(result.getRecursoId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        assertThat(result.getStatus()).isEqualTo("pendente");
        verify(outbox).publish(eq(EventTypes.PLENO_SYNC_REQUESTED), any(Map.class));
    }

    @Test
    @DisplayName("enfileirarIsolado enfileira igual a enfileirar (transacao propria, mesmo efeito)")
    void enfileirarIsolado_mesmoEfeito() {
        when(repository.findBySistemaAndRecursoTipoAndRecursoId("pleno", "imobiliaria", UUID.fromString("00000000-0000-0000-0000-000000000009")))
            .thenReturn(Optional.empty());
        when(repository.save(any(IntegracaoSync.class))).thenAnswer(inv -> {
            IntegracaoSync arg = inv.getArgument(0);
            arg.setId(UUID.fromString("00000000-0000-0000-0000-000000000005"));
            return arg;
        });

        IntegracaoSync result = service.enfileirarIsolado("imobiliaria", UUID.fromString("00000000-0000-0000-0000-000000000009"));

        assertThat(result.getId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000005"));
        assertThat(result.getStatus()).isEqualTo("pendente");
        verify(outbox).publish(eq(EventTypes.PLENO_SYNC_REQUESTED), any(Map.class));
    }

    @Test
    @DisplayName("enfileirar resets status to pendente if exists")
    void enfileirar_resetsStatus() {
        IntegracaoSync existing = new IntegracaoSync();
        existing.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        existing.setRecursoTipo("imovel");
        existing.setRecursoId(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        existing.setStatus("sucesso");
        existing.setErro(null);

        when(repository.findBySistemaAndRecursoTipoAndRecursoId("pleno", "imovel", UUID.fromString("00000000-0000-0000-0000-000000000100")))
            .thenReturn(Optional.of(existing));
        when(repository.save(any(IntegracaoSync.class))).thenReturn(existing);

        service.enfileirar("imovel", UUID.fromString("00000000-0000-0000-0000-000000000100"));

        assertThat(existing.getStatus()).isEqualTo("pendente");
        assertThat(existing.getErro()).isNull();
    }

    @Test
    @DisplayName("enfileirar publishes event with espelhar acao when no externalId")
    void enfileirar_publishesEspelharAction() {
        when(repository.findBySistemaAndRecursoTipoAndRecursoId("pleno", "cliente", UUID.fromString("00000000-0000-0000-0000-000000000200")))
            .thenReturn(Optional.empty());
        when(repository.save(any(IntegracaoSync.class))).thenAnswer(inv -> {
            IntegracaoSync arg = inv.getArgument(0);
            arg.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
            return arg;
        });

        service.enfileirar("cliente", UUID.fromString("00000000-0000-0000-0000-000000000200"));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(outbox).publish(eq(EventTypes.PLENO_SYNC_REQUESTED), captor.capture());
        assertThat(captor.getValue().get("acao")).isEqualTo("espelhar");
    }

    @Test
    @DisplayName("enfileirar publishes sync_status acao when has externalId")
    void enfileirar_publishesSyncStatusAction() {
        IntegracaoSync sync = new IntegracaoSync();
        sync.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        sync.setRecursoTipo("cliente");
        sync.setRecursoId(UUID.fromString("00000000-0000-0000-0000-000000000200"));
        sync.setExternalId("ext-123");

        when(repository.findBySistemaAndRecursoTipoAndRecursoId("pleno", "cliente", UUID.fromString("00000000-0000-0000-0000-000000000200")))
            .thenReturn(Optional.of(sync));
        when(repository.save(any(IntegracaoSync.class))).thenReturn(sync);

        service.enfileirar("cliente", UUID.fromString("00000000-0000-0000-0000-000000000200"));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(outbox).publish(eq(EventTypes.PLENO_SYNC_REQUESTED), captor.capture());
        assertThat(captor.getValue().get("acao")).isEqualTo("sync_status");
    }

    @Test
    @DisplayName("aplicarResultado updates status and error")
    void aplicarResultado_updates() {
        IntegracaoSync sync = new IntegracaoSync();
        sync.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        sync.setStatus("pendente");
        sync.setExternalId(null);
        sync.setErro(null);

        when(repository.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(Optional.of(sync));

        service.aplicarResultado(UUID.fromString("00000000-0000-0000-0000-000000000001"), "sucesso", "ext-456", null);

        assertThat(sync.getStatus()).isEqualTo("sucesso");
        assertThat(sync.getExternalId()).isEqualTo("ext-456");
        assertThat(sync.getErro()).isNull();
        assertThat(sync.getUltimoSync()).isNotNull();
    }

    @Test
    @DisplayName("aplicarResultado sets error message on failure")
    void aplicarResultado_setsError() {
        IntegracaoSync sync = new IntegracaoSync();
        sync.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        sync.setStatus("pendente");

        when(repository.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(Optional.of(sync));

        service.aplicarResultado(UUID.fromString("00000000-0000-0000-0000-000000000001"), "erro", null, "Timeout na API Pleno");

        assertThat(sync.getStatus()).isEqualTo("erro");
        assertThat(sync.getErro()).isEqualTo("Timeout na API Pleno");
    }

    @Test
    @DisplayName("aplicarResultado throws NotFoundException when id not found")
    void aplicarResultado_throwsNotFound() {
        when(repository.findById(UUID.fromString("00000000-0000-0000-0000-000000000999"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.aplicarResultado(UUID.fromString("00000000-0000-0000-0000-000000000999"), "erro", null, "msg"))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("integracao_sync 00000000-0000-0000-0000-000000000999");
    }

    @Test
    @DisplayName("drenarPendentes fetches and republishes pending sync records")
    void drenarPendentes_republishesPending() {
        IntegracaoSync sync1 = new IntegracaoSync();
        sync1.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        sync1.setRecursoTipo("imovel");
        sync1.setRecursoId(UUID.fromString("00000000-0000-0000-0000-000000000100"));
        sync1.setStatus("pendente");
        sync1.setExternalId(null);

        IntegracaoSync sync2 = new IntegracaoSync();
        sync2.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        sync2.setRecursoTipo("cliente");
        sync2.setRecursoId(UUID.fromString("00000000-0000-0000-0000-000000000200"));
        sync2.setStatus("erro");
        sync2.setExternalId("ext-789");

        when(repository.findTop50ByStatusInOrderByIdAsc(
            List.of("pendente", "erro"))).thenReturn(List.of(sync1, sync2));

        service.drenarPendentes();

        verify(outbox, times(2)).publish(eq(EventTypes.PLENO_SYNC_REQUESTED), any(Map.class));
    }

    @Test
    @DisplayName("drenarPendentes handles empty list")
    void drenarPendentes_handlesEmpty() {
        when(repository.findTop50ByStatusInOrderByIdAsc(
            List.of("pendente", "erro"))).thenReturn(List.of());

        service.drenarPendentes();

        verify(outbox, never()).publish(any(), any());
    }

    @Test
    @DisplayName("vincularExterno grava external_id + sincronizado + origem manual, sem publicar (T6.19)")
    void vincularExterno_semEvento() {
        when(repository.findBySistemaAndRecursoTipoAndRecursoId("pleno", "imobiliaria", UUID.fromString("00000000-0000-0000-0000-000000000005")))
            .thenReturn(Optional.empty());
        when(repository.save(any(IntegracaoSync.class))).thenAnswer(inv -> inv.getArgument(0));

        IntegracaoSync row = service.vincularExterno("imobiliaria", UUID.fromString("00000000-0000-0000-0000-000000000005"), "9182");

        assertThat(row.getExternalId()).isEqualTo("9182");
        assertThat(row.getStatus()).isEqualTo("sincronizado");
        assertThat(row.getOrigem()).isEqualTo("manual");
        assertThat(row.getUltimoSync()).isNotNull();
        verify(outbox, never()).publish(any(), any());
    }

    @Test
    @DisplayName("buscar delega ao repo por (pleno, tipo, id)")
    void buscar_delega() {
        IntegracaoSync row = new IntegracaoSync();
        row.setRecursoTipo("imovel");
        row.setRecursoId(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        when(repository.findBySistemaAndRecursoTipoAndRecursoId("pleno", "imovel", UUID.fromString("00000000-0000-0000-0000-000000000003")))
            .thenReturn(Optional.of(row));

        assertThat(service.buscar("imovel", UUID.fromString("00000000-0000-0000-0000-000000000003"))).containsSame(row);
    }
}
