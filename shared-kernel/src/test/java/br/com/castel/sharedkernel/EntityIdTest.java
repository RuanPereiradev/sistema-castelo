package br.com.castel.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class EntityIdTest {

    /** Same shape the concrete IDs of each module follow: validation in the compact constructor. */
    record FooId(UUID value) implements EntityId {
        FooId {
            EntityId.requireValid(value);
        }
    }

    // ---------------------------------------------------------------------
    // of(String, constructor)
    // ---------------------------------------------------------------------

    @Test
    void shouldRejectMalformedUuidTextWithInvalidEntityIdCode() {
        assertThatThrownBy(() -> EntityId.of("not-a-uuid", FooId::new))
                .asInstanceOf(type(InvalidEntityIdException.class))
                .extracting(InvalidEntityIdException::code)
                .isEqualTo("INVALID_ENTITY_ID");
    }

    // ---------------------------------------------------------------------
    // newId(constructor)
    // ---------------------------------------------------------------------

    @Test
    void shouldGenerateDistinctValuesOnSuccessiveCalls() {
        FooId first = EntityId.newId(FooId::new);
        FooId second = EntityId.newId(FooId::new);

        assertThat(first.value()).isNotEqualTo(second.value());
    }

    // ---------------------------------------------------------------------
    // requireValid(UUID)
    // ---------------------------------------------------------------------

    @Test
    void shouldRejectNullValueInRequireValidWithInvalidEntityIdCode() {
        assertThatThrownBy(() -> EntityId.requireValid(null))
                .asInstanceOf(type(InvalidEntityIdException.class))
                .extracting(InvalidEntityIdException::code)
                .isEqualTo("INVALID_ENTITY_ID");
    }

    @Test
    void shouldRejectNullValueInConcreteIdCompactConstructorWithInvalidEntityIdCode() {
        assertThatThrownBy(() -> new FooId(null))
                .asInstanceOf(type(InvalidEntityIdException.class))
                .extracting(InvalidEntityIdException::code)
                .isEqualTo("INVALID_ENTITY_ID");
    }
}
