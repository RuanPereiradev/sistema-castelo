package br.com.castel.restaurant.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The code printed on the QR of the table (decisions #15 and #18 of task qr-table-ordering). */
class DiningTableOrderingTokenTest {

    private static final UUID PROPERTY_ID = UUID.randomUUID();

    @Test
    void shouldBeBornWithAnOrderingTokenOfItsOwn() {
        DiningTable first = DiningTable.create(PROPERTY_ID, "M1", 4, null);
        DiningTable second = DiningTable.create(PROPERTY_ID, "M2", 4, null);

        assertThat(first.orderingToken()).isNotNull();
        assertThat(first.orderingToken()).isNotEqualTo(second.orderingToken());
    }

    @Test
    void shouldReplaceTheOrderingTokenWhenRegenerated() {
        DiningTable table = DiningTable.create(PROPERTY_ID, "M1", 4, null);
        UUID printed = table.orderingToken();

        table.regenerateOrderingToken();

        assertThat(table.orderingToken()).isNotNull().isNotEqualTo(printed);
    }
}
