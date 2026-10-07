package br.com.vistoria.core;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * O schema mora num único {@code db/migration/V1__baseline.sql} (as migrations
 * antigas foram todas dobradas nele; não há V2+). Os testes que fazem análise
 * estática do schema (constraints × enum, RLS, FK) leem o baseline por aqui.
 *
 * <p>{@link #text()} devolve o SQL cru de V1 (== estado físico final: PKs/FKs UUID,
 * todas as colunas de V3–V7, RLS no padrão {@code current_tenant_ids()});
 * {@link #noComments()} remove as linhas de comentário ({@code --}).</p>
 */
public final class BaselineSql {

    private BaselineSql() {}

    private static final String TEXT = load("db/migration/V1__baseline.sql");
    private static final String NO_COMMENTS = TEXT.lines()
            .filter(l -> !l.stripLeading().startsWith("--"))
            .collect(Collectors.joining("\n"));

    public static String text() {
        return TEXT;
    }

    public static String noComments() {
        return NO_COMMENTS;
    }

    private static String load(String resource) {
        InputStream is = BaselineSql.class.getClassLoader().getResourceAsStream(resource);
        if (is == null) {
            throw new IllegalStateException(resource + " não está no test classpath");
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return r.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
