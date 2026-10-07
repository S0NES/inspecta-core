package br.com.vistoria.core.shared.validation;

/**
 * Verificador único de CPF, e-mail e nome completo. O MESMO algoritmo e os MESMOS
 * códigos de erro existem no frontend em {@code inspecta-web/src/utils/pessoaDocumento.ts}
 * — o que passa aqui passa lá. Sem Spring; qualquer módulo Java reusa esta API estática.
 *
 * <p>Códigos de erro estáveis (lançados como mensagem de {@link IllegalArgumentException}):
 * {@code cpf_invalido}, {@code email_invalido}, {@code nome_completo_invalido}.</p>
 */
public final class PessoaDocumento {

    private PessoaDocumento() {}

    public static final String CPF_INVALIDO = "cpf_invalido";
    public static final String CNPJ_INVALIDO = "cnpj_invalido";
    public static final String EMAIL_INVALIDO = "email_invalido";
    public static final String TELEFONE_INVALIDO = "telefone_invalido";
    public static final String NOME_COMPLETO_INVALIDO = "nome_completo_invalido";

    /** RFC 5321: 254 caracteres é o teto de um endereço de e-mail. */
    public static final int EMAIL_MAX = 254;

    /** DDDs válidos no Brasil (Plano Nacional de Numeração). Fora desta lista → telefone inválido. */
    private static final java.util.Set<Integer> DDDS_VALIDOS = java.util.Set.of(
            11, 12, 13, 14, 15, 16, 17, 18, 19,
            21, 22, 24, 27, 28,
            31, 32, 33, 34, 35, 37, 38,
            41, 42, 43, 44, 45, 46, 47, 48, 49,
            51, 53, 54, 55,
            61, 62, 63, 64, 65, 66, 67, 68, 69,
            71, 73, 74, 75, 77, 79,
            81, 82, 83, 84, 85, 86, 87, 88, 89,
            91, 92, 93, 94, 95, 96, 97, 98, 99);

    /**
     * Regex de e-mail — IDÊNTICA à do TS (documentar nos dois lados):
     * local-part + '@' + domínio com ponto; sem espaço. Sem verificação de MX.
     * {@code ^[^\s@]+@[^\s@]+\.[^\s@]+$}
     */
    private static final java.util.regex.Pattern EMAIL =
            java.util.regex.Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");


    /** Aceita {@code 000.000.000-00} ou só dígitos. Recusa: vazio, ≠11 dígitos, todos iguais, DV mód-11 inválido. */
    public static boolean cpfValido(String raw) {
        if (raw == null) return false;
        String d = raw.replaceAll("\\D", "");
        if (d.length() != 11) return false;
        if (d.chars().distinct().count() == 1) return false;
        int[] n = d.chars().map(c -> c - '0').toArray();
        return n[9] == dv(n, 9, 10) && n[10] == dv(n, 10, 11);
    }

    /** @return os 11 dígitos do CPF (a forma persistida). @throws IllegalArgumentException {@code cpf_invalido} */
    public static String requireCpf(String raw) {
        if (!cpfValido(raw)) throw new IllegalArgumentException(CPF_INVALIDO);
        return raw.replaceAll("\\D", "");
    }

    /** DV mód-11 — mesmo critério de {@code DadosFalsos.dv} / {@code DadosFalsosTest.dvOk} (inspecta-worker). */
    private static int dv(int[] n, int len, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < len; i++) soma += n[i] * (pesoInicial - i);
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }


    /** Aceita {@code 00.000.000/0000-00} ou só dígitos. Recusa: vazio, ≠14 dígitos, todos iguais, DV mód-11 inválido. */
    public static boolean cnpjValido(String raw) {
        if (raw == null) return false;
        String d = raw.replaceAll("\\D", "");
        if (d.length() != 14) return false;
        if (d.chars().distinct().count() == 1) return false;
        int[] n = d.chars().map(c -> c - '0').toArray();
        return n[12] == dvCnpj(n, 12) && n[13] == dvCnpj(n, 13);
    }

    /** @return os 14 dígitos do CNPJ (a forma persistida). @throws IllegalArgumentException {@code cnpj_invalido} */
    public static String requireCnpj(String raw) {
        if (!cnpjValido(raw)) throw new IllegalArgumentException(CNPJ_INVALIDO);
        return raw.replaceAll("\\D", "");
    }

    /** DV mód-11 do CNPJ: pesos cíclicos 2..9 da direita para a esquerda. */
    private static int dvCnpj(int[] n, int len) {
        int soma = 0, peso = 2;
        for (int i = len - 1; i >= 0; i--) {
            soma += n[i] * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }


    /** Aceita máscara ou só dígitos. Exige 10 (fixo) ou 11 (celular) dígitos e DDD válido. */
    public static boolean telefoneValido(String raw) {
        if (raw == null) return false;
        String d = raw.replaceAll("\\D", "");
        if (d.length() != 10 && d.length() != 11) return false;
        if (d.chars().distinct().count() == 1) return false;
        if (!DDDS_VALIDOS.contains(Integer.parseInt(d.substring(0, 2)))) return false;
        // Celular (11 dígitos): o terceiro dígito é sempre 9.
        return d.length() != 11 || d.charAt(2) == '9';
    }

    /** @return os 10-11 dígitos do telefone (a forma persistida). @throws IllegalArgumentException {@code telefone_invalido} */
    public static String requireTelefone(String raw) {
        if (!telefoneValido(raw)) throw new IllegalArgumentException(TELEFONE_INVALIDO);
        return raw.replaceAll("\\D", "");
    }


    public static boolean emailValido(String raw) {
        if (raw == null) return false;
        String e = raw.trim();
        return !e.isEmpty() && e.length() <= EMAIL_MAX && EMAIL.matcher(e).matches();
    }

    /** @return o e-mail em lower-case (a forma persistida). @throws IllegalArgumentException {@code email_invalido} */
    public static String requireEmail(String raw) {
        if (!emailValido(raw)) throw new IllegalArgumentException(EMAIL_INVALIDO);
        return raw.trim().toLowerCase();
    }


    /** trim; ≥2 palavras com ≥2 letras cada. Mesma regra do TS. */
    public static boolean nomeCompletoValido(String raw) {
        if (raw == null) return false;
        String[] partes = raw.trim().split("\\s+");
        int palavrasOk = 0;
        for (String p : partes) {
            long letras = p.chars().filter(Character::isLetter).count();
            if (letras >= 2) palavrasOk++;
        }
        return palavrasOk >= 2;
    }

    /** @return o nome com trim. @throws IllegalArgumentException {@code nome_completo_invalido} */
    public static String requireNomeCompleto(String raw) {
        if (!nomeCompletoValido(raw)) throw new IllegalArgumentException(NOME_COMPLETO_INVALIDO);
        return raw.trim();
    }
}
