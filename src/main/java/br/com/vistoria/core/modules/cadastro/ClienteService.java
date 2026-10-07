package br.com.vistoria.core.modules.cadastro;

import br.com.vistoria.core.modules.cadastro.CadastroRepositories.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clientes;

    @Transactional(readOnly = true)
    public Optional<String> emailPorId(UUID clienteId) {
        // Solicitacao pode nao ter cliente (V24) — findById(null) lancaria.
        if (clienteId == null) return Optional.empty();
        return clientes.findById(clienteId).map(Cliente::getEmail);
    }
}
