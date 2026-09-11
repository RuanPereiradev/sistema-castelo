package br.com.castel.sharedkernel;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
