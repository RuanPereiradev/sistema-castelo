package br.com.castel.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Each subclass is obtained by triggering the real domain rule that throws it,
 * so the test does not depend on exception constructors.
 */
class DomainExceptionTest {

    private static final String UPPER_SNAKE_CASE = "^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$";
    private static final LocalDate OCT_01 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCT_04 = LocalDate.of(2026, 10, 4);

    /** exception name, trigger, a second trigger with different input, expected code. */
    static Stream<Arguments> domainExceptions() {
        return Stream.of(
                arguments(
                        "CurrencyMismatchException",
                        (ThrowingCallable) () -> Money.of("10.00").plus(Money.of("10.00", Currency.getInstance("USD"))),
                        (ThrowingCallable) () -> Money.of("1.00", Currency.getInstance("EUR")).plus(Money.of("1.00")),
                        "CURRENCY_MISMATCH"),
                arguments(
                        "InvalidMoneyException",
                        (ThrowingCallable) () -> Money.of("abc"),
                        (ThrowingCallable) () -> Money.of((String) null),
                        "INVALID_MONEY"),
                arguments(
                        "InvalidPercentageException",
                        (ThrowingCallable) () -> Percentage.ofPercent(-1),
                        (ThrowingCallable) () -> Percentage.ofFraction(new BigDecimal("-0.50")),
                        "INVALID_PERCENTAGE"),
                arguments(
                        "InvalidQuantityException",
                        (ThrowingCallable) () -> Quantity.of(0),
                        (ThrowingCallable) () -> Quantity.of(-5),
                        "INVALID_QUANTITY"),
                arguments(
                        "InvalidWeightException",
                        (ThrowingCallable) () -> Weight.ofGrams(0),
                        (ThrowingCallable) () -> Weight.ofGrams(-100),
                        "INVALID_WEIGHT"),
                arguments(
                        "InvalidDateRangeException",
                        (ThrowingCallable) () -> DateRange.of(OCT_01, OCT_01),
                        (ThrowingCallable) () -> DateRange.of(OCT_04, OCT_01),
                        "INVALID_DATE_RANGE"),
                arguments(
                        "InvalidCpfException",
                        (ThrowingCallable) () -> Cpf.of("11111111111"),
                        (ThrowingCallable) () -> Cpf.of("123"),
                        "INVALID_CPF"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("domainExceptions")
    void shouldBeSubclassOfDomainException(
            String exceptionName, ThrowingCallable trigger, ThrowingCallable otherTrigger, String expectedCode) {
        assertThatThrownBy(trigger).isInstanceOf(DomainException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("domainExceptions")
    void shouldExposeNonNullCode(
            String exceptionName, ThrowingCallable trigger, ThrowingCallable otherTrigger, String expectedCode) {
        assertThatThrownBy(trigger)
                .asInstanceOf(type(DomainException.class))
                .extracting(DomainException::code)
                .isNotNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("domainExceptions")
    void shouldExposeCodeInUpperSnakeCase(
            String exceptionName, ThrowingCallable trigger, ThrowingCallable otherTrigger, String expectedCode) {
        assertThatThrownBy(trigger)
                .asInstanceOf(type(DomainException.class))
                .extracting(DomainException::code, STRING)
                .matches(UPPER_SNAKE_CASE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("domainExceptions")
    void shouldExposeStableCodeDerivedFromExceptionName(
            String exceptionName, ThrowingCallable trigger, ThrowingCallable otherTrigger, String expectedCode) {
        assertThatThrownBy(trigger)
                .asInstanceOf(type(DomainException.class))
                .extracting(DomainException::code)
                .isEqualTo(expectedCode);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("domainExceptions")
    void shouldExposeSameCodeRegardlessOfOffendingInput(
            String exceptionName, ThrowingCallable trigger, ThrowingCallable otherTrigger, String expectedCode) {
        DomainException first = (DomainException) catchThrowable(trigger);
        DomainException second = (DomainException) catchThrowable(otherTrigger);

        assertThat(first.code()).isEqualTo(second.code());
    }
}
