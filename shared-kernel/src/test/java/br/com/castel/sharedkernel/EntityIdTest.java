package br.com.castel.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EntityIdTest {

    /** RFC 9562 variant (bits 10x), as reported by {@link UUID#variant()}. */
    private static final int RFC_9562_VARIANT = 2;

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

    @Test
    void shouldGenerateUuidVersion7ForNewId() {
        FooId id = EntityId.newId(FooId::new);

        assertThat(id.value().version()).isEqualTo(7);
    }

    @Test
    void shouldGenerateRfc9562VariantForNewId() {
        FooId id = EntityId.newId(FooId::new);

        assertThat(id.value().variant()).isEqualTo(RFC_9562_VARIANT);
    }

    /**
     * UUIDv7 = 48-bit ms timestamp + version + variant + random bits. Ordering is only
     * guaranteed across distinct milliseconds, so each value is generated after the clock
     * advances. Comparison is unsigned: canonical lowercase hex text sorts lexicographically
     * in the same order as the unsigned 128-bit value ({@link UUID#compareTo} is signed).
     */
    @Test
    void shouldGenerateIncreasingValuesWhenCalledInDistinctMilliseconds() throws InterruptedException {
        List<String> generatedInOrder = generateIdTextsInDistinctMilliseconds(5);

        assertThat(generatedInOrder)
                .doesNotHaveDuplicates()
                .isSorted();
    }

    // ---------------------------------------------------------------------
    // newPublicToken()
    // ---------------------------------------------------------------------

    @Test
    void shouldGenerateUuidVersion4ForPublicToken() {
        UUID token = EntityId.newPublicToken();

        assertThat(token.version()).isEqualTo(4);
    }

    @Test
    void shouldGenerateRfc9562VariantForPublicToken() {
        UUID token = EntityId.newPublicToken();

        assertThat(token.variant()).isEqualTo(RFC_9562_VARIANT);
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

    // ---------------------------------------------------------------------
    // Arrange helper
    // ---------------------------------------------------------------------

    private static List<String> generateIdTextsInDistinctMilliseconds(int count) throws InterruptedException {
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            texts.add(EntityId.newId(FooId::new).value().toString());
            Thread.sleep(2);
        }
        return texts;
    }
}
