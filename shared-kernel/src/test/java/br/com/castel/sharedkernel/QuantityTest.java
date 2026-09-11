package br.com.castel.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import org.junit.jupiter.api.Test;

class QuantityTest {

    @Test
    void shouldRejectZeroQuantity() {
        assertThatThrownBy(() -> Quantity.of(0))
                .isInstanceOf(InvalidQuantityException.class);
    }

    @Test
    void shouldRejectNegativeQuantity() {
        assertThatThrownBy(() -> Quantity.of(-1))
                .isInstanceOf(InvalidQuantityException.class);
    }

    @Test
    void shouldAcceptQuantityOfOne() {
        assertThatCode(() -> Quantity.of(1)).doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptQuantityOf999() {
        assertThatCode(() -> Quantity.of(999)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectQuantityOf1000() {
        assertThatThrownBy(() -> Quantity.of(1000))
                .isInstanceOf(InvalidQuantityException.class);
    }

    @Test
    void shouldAcceptAdditionReachingExactUpperLimitOf999() {
        Quantity result = Quantity.of(499).plus(Quantity.of(500));

        assertThat(result).isEqualTo(Quantity.of(999));
    }

    @Test
    void shouldRejectAdditionExceedingUpperLimitWithInvalidQuantityCode() {
        Quantity fiveHundred = Quantity.of(500);

        assertThatThrownBy(() -> fiveHundred.plus(Quantity.of(500)))
                .asInstanceOf(type(InvalidQuantityException.class))
                .extracting(InvalidQuantityException::code)
                .isEqualTo("INVALID_QUANTITY");
    }
}
