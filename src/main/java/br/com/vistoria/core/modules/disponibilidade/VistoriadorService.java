package br.com.vistoria.core.modules.disponibilidade;

import br.com.vistoria.core.modules.disponibilidade.DisponibilidadeRepositories.VistoriadorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VistoriadorService {

    private final VistoriadorRepository vistoriadores;

    @Transactional(readOnly = true)
    public Optional<UUID> idPorUsuario(UUID usuarioId) {
        return vistoriadores.findByUsuarioId(usuarioId).map(Vistoriador::getId);
    }
}
