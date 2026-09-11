package br.com.castel.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Each subclass is obtained by triggering the real domain rule that throws it,
 * so the test does not depend on exception constructors.
 *
 * <p>The {@code MONEY_SCALE_EXCEEDED} and {@code MONEY_OUT_OF_RANGE} codes of
 * {@link InvalidMoneyException} are covered in {@code MoneyTest}; here Money uses its
 * canonical trigger ({@code of("abc")}).
 */
class DomainExceptionTest {

    private static final String UPPER_SNAKE_CASE = "^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$";
    private static final LocalDate OCT_01 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCT_04 = LocalDate.of(2026, 10, 4);

    record SampleId(UUID value) implements EntityId {
        SampleId {
            EntityId.requireValid(value);
        }
    }

    /** exception name, canonical trigger, expected code. */
    static Stream<Arguments> domainExceptions() {
        return Stream.of(
                arguments(
                        "InvalidMoneyException",
                        (ThrowingCallable) () -> Money.of("abc"),
                        "INVALID_MONEY"),
                arguments(
                        "InvalidPercentageException",
                        (ThrowingCallable) () -> Percentage.ofPercent(-1),
                        "INVALID_PERCENTAGE"),
                arguments(
                        "InvalidQuantityException",
                        (ThrowingCallable) () -> Quantity.of(0),
                        "INVALID_QUANTITY"),
                arguments(
                        "InvalidWeightException",
                        (ThrowingCallable) () -> Weight.ofGrams(0),
                        "INVALID_WEIGHT"),
                arguments(
                        "InvalidDateRangeException",
                        (ThrowingCallable) () -> DateRange.of(OCT_01, OCT_01),
                        "INVALID_DATE_RANGE"),
                arguments(
                        "InvalidCpfException",
                        (ThrowingCallable) () -> Cpf.of("11111111111"),
                        "INVALID_CPF"),
                arguments(
                        "InvalidEntityIdException",
                        (ThrowingCallable) () -> EntityId.requireValid(null),
                        "INVALID_ENTITY_ID"));
    }

    /**
     * Two different violations of the same rule, for subclasses that have a single code.
     * InvalidMoneyException is excluded: by spec it has more than one code.
     */
    static Stream<Arguments> singleCodeExceptionsWithTwoViolations() {
        return Stream.of(
                arguments(
                        "InvalidPercentageException",
                        (ThrowingCallable) () -> Percentage.ofPercent(-1),
                        (ThrowingCallable) () -> Percentage.ofFraction(new BigDecimal("-0.50"))),
                arguments(
                        "InvalidQuantityException",
                        (ThrowingCallable) () -> Quantity.of(0),
                        (ThrowingCallable) () -> Quantity.of(1000)),
                arguments(
                        "InvalidWeightException",
                        (ThrowingCallable) () -> Weight.ofGrams(0),
                        (ThrowingCallable) () -> Weight.ofGrams(-100)),
                arguments(
                        "InvalidDateRangeException",
                        (ThrowingCallable) () -> DateRange.of(OCT_01, OCT_01),
                        (ThrowingCallable) () -> DateRange.of(OCT_04, OCT_01)),
                arguments(
                        "InvalidCpfException",
                        (ThrowingCallable) () -> Cpf.of("11111111111"),
                        (ThrowingCallable) () -> Cpf.of("123")),
                arguments(
                        "InvalidEntityIdException",
                        (ThrowingCallable) () -> EntityId.requireValid(null),
                        (ThrowingCallable) () -> EntityId.of("not-a-uuid", SampleId::new)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("domainExceptions")
    void shouldBeSubclassOfDomainException(String exceptionName, ThrowingCallable trigger, String expectedCode) {
        assertThatThrownBy(trigger).isInstanceOf(DomainException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("domainExceptions")
    void shouldExposeNonNullCode(String exceptionName, ThrowingCallable trigger, String expectedCode) {
        assertThatThrownBy(trigger)
                .asInstanceOf(type(DomainException.class))
                .extracting(DomainException::code)
                .isNotNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("domainExceptions")
    void shouldExposeCodeInUpperSnakeCase(String exceptionName, ThrowingCallable trigger, String expectedCode) {
        assertThatThrownBy(trigger)
                .asInstanceOf(type(DomainException.class))
                .extracting(DomainException::code, STRING)
                .matches(UPPER_SNAKE_CASE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("domainExceptions")
    void shouldExposeExpectedStableCode(String exceptionName, ThrowingCallable trigger, String expectedCode) {
        assertThatThrownBy(trigger)
                .asInstanceOf(type(DomainException.class))
                .extracting(DomainException::code)
                .isEqualTo(expectedCode);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("singleCodeExceptionsWithTwoViolations")
    void shouldExposeSameCodeForDifferentViolationsOfSameRule(
            String exceptionName, ThrowingCallable firstViolation, ThrowingCallable secondViolation) {
        DomainException first = (DomainException) catchThrowable(firstViolation);
        DomainException second = (DomainException) catchThrowable(secondViolation);

        assertThat(first.code()).isEqualTo(second.code());
    }
}
