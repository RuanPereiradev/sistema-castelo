package br.com.castel.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Fixtures (check digits computed by hand and verified by script):
 * <ul>
 *   <li>{@code 52998224725}: valid</li>
 *   <li>{@code 12345678909}: valid; first check digit is 0 (remainder &lt; 2 rule)</li>
 *   <li>{@code 52998224726}: first check digit correct, second wrong</li>
 *   <li>{@code 52998224733}: first check digit wrong (should be 2), second consistent
 *       with the wrong first one, so only the first-digit check rejects it</li>
 * </ul>
 */
class CpfTest {

    private static final String VALID_CPF = "52998224725";
    private static final String VALID_CPF_WITH_ZERO_FIRST_CHECK_DIGIT = "12345678909";

    @Test
    void shouldAcceptKnownValidCpf() {
        assertThatCode(() -> Cpf.of(VALID_CPF)).doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptValidCpfWhoseFirstCheckDigitIsZero() {
        assertThatCode(() -> Cpf.of(VALID_CPF_WITH_ZERO_FIRST_CHECK_DIGIT)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectCpfWhenLastCheckDigitIsWrong() {
        assertThatThrownBy(() -> Cpf.of("52998224726"))
                .isInstanceOf(InvalidCpfException.class);
    }

    @Test
    void shouldRejectCpfWhenFirstCheckDigitIsWrong() {
        assertThatThrownBy(() -> Cpf.of("52998224733"))
                .isInstanceOf(InvalidCpfException.class);
    }

    @Test
    void shouldRejectCpfWithAllDigitsOne() {
        assertThatThrownBy(() -> Cpf.of("11111111111"))
                .isInstanceOf(InvalidCpfException.class);
    }

    @Test
    void shouldRejectCpfWithAllDigitsZero() {
        assertThatThrownBy(() -> Cpf.of("00000000000"))
                .isInstanceOf(InvalidCpfException.class);
    }

    @Test
    void shouldConsiderPunctuatedAndUnpunctuatedCpfEqual() {
        Cpf punctuated = Cpf.of("529.982.247-25");
        Cpf unpunctuated = Cpf.of(VALID_CPF);

        assertThat(punctuated).isEqualTo(unpunctuated);
    }

    @Test
    void shouldRejectCpfWithFewerThanElevenDigits() {
        assertThatThrownBy(() -> Cpf.of("5299822472"))
                .isInstanceOf(InvalidCpfException.class);
    }

    @Test
    void shouldRejectCpfWithMoreThanElevenDigits() {
        assertThatThrownBy(() -> Cpf.of("529982247250"))
                .isInstanceOf(InvalidCpfException.class);
    }

    @Test
    void shouldRejectCpfContainingLettersEvenWhenRemainingDigitsFormValidCpf() {
        assertThatThrownBy(() -> Cpf.of("529982247a25"))
                .isInstanceOf(InvalidCpfException.class);
    }

    @Test
    void shouldRejectNullCpf() {
        assertThatThrownBy(() -> Cpf.of(null))
                .isInstanceOf(InvalidCpfException.class);
    }

    @Test
    void shouldRejectEmptyCpf() {
        assertThatThrownBy(() -> Cpf.of(""))
                .isInstanceOf(InvalidCpfException.class);
    }

    @Test
    void shouldFormatCpfWithDotsAndDash() {
        Cpf cpf = Cpf.of(VALID_CPF_WITH_ZERO_FIRST_CHECK_DIGIT);

        assertThat(cpf.formatted()).isEqualTo("123.456.789-09");
    }
}
