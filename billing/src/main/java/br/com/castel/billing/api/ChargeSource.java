package br.com.castel.billing.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Where a posting came from: what it charges for and the id of the thing in the module that posted
 * it (decision #9 of task 1.3).
 *
 * <p>The id is opaque to billing, as {@link FolioOwner} is: billing stores it and gives it back, and
 * never looks behind it.
 */
public record ChargeSource(ChargeSourceType type, UUID id) {

    public ChargeSource {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
    }

    public static ChargeSource roomNight(UUID id) {
        return new ChargeSource(ChargeSourceType.ROOM_NIGHT, id);
    }

    public static ChargeSource tab(UUID id) {
        return new ChargeSource(ChargeSourceType.TAB, id);
    }
}
