package br.com.castel.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class EntityIdTest {

    private static final UUID SAME_UUID = UUID.fromString("3f2b8c1e-7a4d-4e1b-9c6f-2d8a5b7e1c90");

    /**
     * Baseline: without value equality within the same type, the cross-type
     * inequality test below would pass trivially through identity equality.
     */
    @Test
    void shouldConsiderIdsOfSameTypeWithSameUuidEqual() {
        TabId first = TabId.of(SAME_UUID);
        TabId second = TabId.of(SAME_UUID);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void shouldNotConsiderIdsOfDifferentTypesWithSameUuidEqual() {
        TabId tabId = TabId.of(SAME_UUID);
        FolioId folioId = FolioId.of(SAME_UUID);

        assertThat(tabId).isNotEqualTo(folioId);
    }

    /**
     * Executable stand-in for "assigning a TabId to a FolioId variable does not
     * compile": the Java assignment {@code FolioId x = tabId;} compiles only if
     * FolioId is assignable from TabId.
     */
    @Test
    void shouldNotAllowTabIdToBeAssignedToFolioId() {
        assertThat(FolioId.class.isAssignableFrom(TabId.class)).isFalse();
    }

    @Test
    void shouldNotAllowFolioIdToBeAssignedToTabId() {
        assertThat(TabId.class.isAssignableFrom(FolioId.class)).isFalse();
    }
}
