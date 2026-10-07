package br.com.vistoria.core.modules.cadastro;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CRUD dos ambientes (cômodos) de um imóvel e das fotos de cada ambiente. Isolamento
 * de tenant é garantido pela RLS de {@code ambientes}/{@code fotos} (via
 * {@code imoveis.imobiliaria_id}); esta camada não reimplementa a checagem.
 */
@Service
@RequiredArgsConstructor
public class AmbienteService {

    private final AmbienteRepository ambientes;
    private final FotoRepository fotos;

    @Transactional
    public Ambiente criar(Ambiente ambiente) {
        return ambientes.save(ambiente);
    }

    @Transactional(readOnly = true)
    public List<Ambiente> listarPorImovel(UUID imovelId) {
        return ambientes.findByImovelIdOrderByOrdemAsc(imovelId);
    }

    /** {@code fotos.ambiente_id} é {@code ON DELETE CASCADE}: remove o ambiente já leva as fotos. */
    @Transactional
    public void remover(UUID ambienteId) {
        ambientes.deleteById(ambienteId);
    }

    @Transactional
    public Foto adicionarFoto(Foto foto) {
        return fotos.save(foto);
    }

    @Transactional(readOnly = true)
    public List<Foto> listarFotos(UUID ambienteId) {
        return fotos.findByAmbienteIdOrderByOrdemAsc(ambienteId);
    }

    @Transactional
    public void removerFoto(UUID fotoId) {
        fotos.deleteById(fotoId);
    }
}
