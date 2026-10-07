package br.com.vistoria.core.modules.publico;

import br.com.vistoria.core.auth.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Emissao de links publicos a partir de outros modulos (hoje: o QR de galeria de fotos que o
 * PDF do laudo embute, gerado em {@code inspecta-worker}). {@code PublicoController} continua dono
 * do fluxo manual (staff pedindo um link pelo endpoint autenticado); este service cobre o
 * caminho automatico, que precisa do token bruto (nunca recuperavel depois, so o hash e
 * persistido) no mesmo instante em que a HTML do laudo esta sendo montada.
 */
@Service
@RequiredArgsConstructor
public class AcessoPublicoService {

    private static final Duration VALIDADE = Duration.ofDays(90);

    private final AcessoPublicoRepository acessos;

    @Transactional
    public String criarLinkImagens(UUID laudoId) {
        String raw = TokenHasher.newToken();
        AcessoPublico a = new AcessoPublico();
        a.setTokenHash(TokenHasher.sha256(raw));
        a.setTipo(AcessoPublico.Tipo.imagens);
        a.setRecursoId(laudoId);
        a.setExpiraEm(Instant.now().plus(VALIDADE));
        acessos.save(a);
        return raw;
    }
}
