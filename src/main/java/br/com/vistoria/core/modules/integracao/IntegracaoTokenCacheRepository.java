package br.com.vistoria.core.modules.integracao;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegracaoTokenCacheRepository extends JpaRepository<IntegracaoTokenCache, String> {
}
