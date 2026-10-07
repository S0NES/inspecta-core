package br.com.vistoria.core.shared.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vetores compartilhados com {@code inspecta-web/src/utils/pessoaDocumento.test.ts} — o que
 * passa aqui tem que passar lá.
 */
@DisplayName("PessoaDocumento")
class PessoaDocumentoTest {

    @Test
    @DisplayName("CPF válido — com e sem máscara")
    void cpf_valido() {
        assertThat(PessoaDocumento.cpfValido("11144477735")).isTrue();
        assertThat(PessoaDocumento.cpfValido("111.444.777-35")).isTrue();
        assertThat(PessoaDocumento.requireCpf("111.444.777-35")).isEqualTo("11144477735");
    }

    @Test
    @DisplayName("CPF inválido — vazio, curto, repetido, DV errado")
    void cpf_invalido() {
        for (String bad : new String[]{null, "", "123456789", "11111111111", "11144477734", "000.000.000-00"}) {
            assertThat(PessoaDocumento.cpfValido(bad)).as("cpf %s", bad).isFalse();
        }
        assertThatThrownBy(() -> PessoaDocumento.requireCpf("11144477734"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("cpf_invalido");
    }

    @Test
    @DisplayName("e-mail — trim + lower na gravação; recusa vazio / sem @ / sem ponto / com espaço")
    void email() {
        assertThat(PessoaDocumento.emailValido("a@b.co")).isTrue();
        assertThat(PessoaDocumento.requireEmail("  Foo.Bar@Example.COM ")).isEqualTo("foo.bar@example.com");
        for (String bad : new String[]{null, "", "foo", "foo@bar", "a b@c.com", "@c.com"}) {
            assertThat(PessoaDocumento.emailValido(bad)).as("email %s", bad).isFalse();
        }
        assertThatThrownBy(() -> PessoaDocumento.requireEmail("foo"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("email_invalido");
    }

    @Test
    @DisplayName("CNPJ válido — com e sem máscara; requireCnpj devolve só dígitos")
    void cnpj_valido() {
        assertThat(PessoaDocumento.cnpjValido("11222333000181")).isTrue();
        assertThat(PessoaDocumento.cnpjValido("11.222.333/0001-81")).isTrue();
        assertThat(PessoaDocumento.requireCnpj("11.222.333/0001-81")).isEqualTo("11222333000181");
    }

    @Test
    @DisplayName("CNPJ inválido — vazio, curto, sequência repetida, DV errado")
    void cnpj_invalido() {
        for (String bad : new String[]{null, "", "1122233300018", "11111111111111", "11222333000180"}) {
            assertThat(PessoaDocumento.cnpjValido(bad)).as("cnpj %s", bad).isFalse();
        }
        assertThatThrownBy(() -> PessoaDocumento.requireCnpj("11222333000180"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("cnpj_invalido");
    }

    @Test
    @DisplayName("telefone BR — 10/11 dígitos, DDD válido, celular com 9; requireTelefone devolve só dígitos")
    void telefone() {
        assertThat(PessoaDocumento.telefoneValido("11987654321")).isTrue();   // celular
        assertThat(PessoaDocumento.telefoneValido("(11) 3456-7890")).isTrue(); // fixo
        assertThat(PessoaDocumento.requireTelefone("(11) 98765-4321")).isEqualTo("11987654321");
        for (String bad : new String[]{null, "", "119876543", "119876543210", "10987654321", "11887654321"}) {
            assertThat(PessoaDocumento.telefoneValido(bad)).as("tel %s", bad).isFalse();
        }
        assertThatThrownBy(() -> PessoaDocumento.requireTelefone("00000000000"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("telefone_invalido");
    }

    @Test
    @DisplayName("nome completo — ≥2 palavras com ≥2 letras cada")
    void nome() {
        assertThat(PessoaDocumento.nomeCompletoValido("Ana Silva")).isTrue();
        assertThat(PessoaDocumento.requireNomeCompleto("  João   Souza  ")).isEqualTo("João   Souza");
        for (String bad : new String[]{null, "", "   ", "Ana", "A S", "Ana S"}) {
            assertThat(PessoaDocumento.nomeCompletoValido(bad)).as("nome %s", bad).isFalse();
        }
        assertThatThrownBy(() -> PessoaDocumento.requireNomeCompleto("Ana"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("nome_completo_invalido");
    }
}
