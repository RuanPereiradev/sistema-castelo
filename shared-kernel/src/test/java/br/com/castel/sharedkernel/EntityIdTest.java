package br.com.castel.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class EntityIdTest {

    private static final UUID SAME_UUID = UUID.fromString("3f2b8c1e-7a4d-4e1b-9c6f-2d8a5b7e1c90");

    record FooId(UUID value) implements EntityId {}

    record BarId(UUID value) implements EntityId {}

    @Test
    void shouldNotConsiderIdsOfDifferentTypesWithSameUuidEqual() {
        FooId fooId = new FooId(SAME_UUID);
        BarId barId = new BarId(SAME_UUID);

        assertThat(fooId).isNotEqualTo(barId);
    }
}
