package br.com.vistoria.core.config;

import java.util.UUID;
import java.util.List;
import java.util.Objects;

/**
 * Tenant(s) da requisição em curso.
 *
 * <p>Um usuário pode estar vinculado a mais de uma imobiliária
 * (`usuario_imobiliarias`, V25). O contexto guarda a lista inteira; a RLS
 * (`app.current_tenants`, V26) filtra por qualquer uma delas. Lista vazia /
 * não setada = perfil global (admin/suporte): vê tudo.</p>
 *
 * <p>{@link #get()} devolve a imobiliária <b>primária</b> (a 1ª da lista) e é o
 * que os caminhos de <b>escrita</b> usam quando precisam de um único tenant.</p>
 */
public final class TenantContext {

    private static final ThreadLocal<List<UUID>> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    /** Vínculo único (compat). */
    public static void set(UUID imobiliariaId) {
        CURRENT.set(imobiliariaId == null ? List.of() : List.of(imobiliariaId));
    }

    /** Todos os vínculos do usuário. Null/empty = vê tudo. */
    public static void set(List<UUID> imobiliariaIds) {
        CURRENT.set(imobiliariaIds == null ? List.of()
                : imobiliariaIds.stream().filter(Objects::nonNull).distinct().toList());
    }

    /** Imobiliária primária (1ª da lista) ou null. Usado por caminhos de escrita. */
    public static UUID get() {
        List<UUID> ids = CURRENT.get();
        return ids == null || ids.isEmpty() ? null : ids.get(0);
    }

    /** Todos os vínculos. Nunca null (lista vazia = sem tenant setado). */
    public static List<UUID> getAll() {
        List<UUID> ids = CURRENT.get();
        return ids == null ? List.of() : ids;
    }

    public static void clear() { CURRENT.remove(); }
}
