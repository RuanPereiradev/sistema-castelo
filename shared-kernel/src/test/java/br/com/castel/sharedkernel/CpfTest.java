package br.com.castel.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Fixtures (check digits computed by hand and verified by script):
 * <ul>
 *   <li>{@code 12345678909}: valid reference CPF (first check digit 0, second 9)</li>
 *   <li>{@code 12345678908}: first check digit correct, second wrong</li>
 *   <li>{@code 12345678917}: first check digit wrong (1 instead of 0); second digit 7 is
 *       the one computed from that wrong first digit, so only the first-digit check rejects it</li>
 *   <li>{@code 11111111111} and {@code 00000000000}: pass the check-digit arithmetic, so only
 *       the repeated-digit rule rejects them</li>
 * </ul>
 */
class CpfTest {

    private static final String VALID_CPF = "12345678909";

    // ---------------------------------------------------------------------
    // Check digits
    // ---------------------------------------------------------------------

    @Test
    void shouldAcceptValidReferenceCpf() {
        assertThatCode(() -> Cpf.of(VALID_CPF)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectCpfWhenLastCheckDigitIsWrong() {
        assertThatThrownBy(() -> Cpf.of("12345678908"))
                .isInstanceOf(InvalidCpfException.class);
    }

    @Test
    void shouldRejectCpfWhenFirstCheckDigitIsWrong() {
        assertThatThrownBy(() -> Cpf.of("12345678917"))
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

    // ---------------------------------------------------------------------
    // Normalization and formatting
    // ---------------------------------------------------------------------

    @Test
    void shouldConsiderCpfWithDotsAndDashEqualToDigitsOnlyCpf() {
        Cpf punctuated = Cpf.of("123.456.789-09");
        Cpf digitsOnly = Cpf.of(VALID_CPF);

        assertThat(punctuated).isEqualTo(digitsOnly);
    }

    @Test
    void shouldConsiderCpfWithDotsAndSpaceEqualToDigitsOnlyCpf() {
        Cpf withSpace = Cpf.of("123.456.789 09");
        Cpf digitsOnly = Cpf.of(VALID_CPF);

        assertThat(withSpace).isEqualTo(digitsOnly);
    }

    @Test
    void shouldFormatCpfWithDotsAndDash() {
        Cpf cpf = Cpf.of(VALID_CPF);

        assertThat(cpf.formatted()).isEqualTo("123.456.789-09");
    }

    // ---------------------------------------------------------------------
    // Malformed input
    // ---------------------------------------------------------------------

    @Test
    void shouldRejectCpfWithFewerThanElevenDigits() {
        assertThatThrownBy(() -> Cpf.of("1234567890"))
                .isInstanceOf(InvalidCpfException.class);
    }

    @Test
    void shouldRejectCpfWithMoreThanElevenDigits() {
        assertThatThrownBy(() -> Cpf.of("123456789090"))
                .isInstanceOf(InvalidCpfException.class);
    }

    @Test
    void shouldRejectCpfContainingLetterEvenWhenRemainingDigitsFormValidCpf() {
        assertThatThrownBy(() -> Cpf.of("123456789a09"))
                .isInstanceOf(InvalidCpfException.class);
    }

    @Test
    void shouldRejectCpfContainingNonSeparatorSymbolEvenWhenRemainingDigitsFormValidCpf() {
        assertThatThrownBy(() -> Cpf.of("123456789#09"))
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
}
