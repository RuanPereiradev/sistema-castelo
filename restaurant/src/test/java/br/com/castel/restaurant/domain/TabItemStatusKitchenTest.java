package br.com.castel.restaurant.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The kitchen display behaviour carried by {@link TabItemStatus}, task 3.5 section 3 (the
 * status x operation matrix and the {@code isOnKitchenQueue} table). Every value is listed, so a
 * status added later without a decision fails here instead of slipping through.
 */
class TabItemStatusKitchenTest {

    @ParameterizedTest(name = "{0}: queue={1} start={2} ready={3} deliver={4} undo={5}")
    @CsvSource({
            "PENDING,        true,  true,  true,  true,  false",
            "IN_PREPARATION, true,  false, true,  true,  true",
            "READY,          true,  false, false, true,  true",
            "DELIVERED,      false, false, false, false, false",
            "CANCELLED,      false, false, false, false, false"
    })
    void shouldFollowTheKitchenMatrixOfTheSpec(TabItemStatus status, boolean onQueue, boolean start,
            boolean ready, boolean delivery, boolean undo) {
        assertThat(status.isOnKitchenQueue()).as("isOnKitchenQueue").isEqualTo(onQueue);
        assertThat(status.acceptsPreparationStart()).as("acceptsPreparationStart").isEqualTo(start);
        assertThat(status.acceptsReady()).as("acceptsReady").isEqualTo(ready);
        assertThat(status.acceptsDelivery()).as("acceptsDelivery").isEqualTo(delivery);
        assertThat(status.acceptsUndo()).as("acceptsUndo").isEqualTo(undo);
    }

    @Test
    void shouldListEveryStatusInTheMatrix() {
        assertThat(TabItemStatus.values()).containsExactlyInAnyOrder(TabItemStatus.PENDING,
                TabItemStatus.IN_PREPARATION, TabItemStatus.READY, TabItemStatus.DELIVERED, TabItemStatus.CANCELLED);
    }
}
