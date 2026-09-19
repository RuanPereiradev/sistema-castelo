package br.com.castel.billing.api;

import java.util.Objects;
import java.util.UUID;

/**
 * What a folio was opened for, as the billing module sees it: a kind and an opaque identifier.
 *
 * <p>Deliberately not the owning module's typed id. The dependency graph lets {@code hotel} and
 * {@code restaurant} see {@code billing.api}, never the other way round, so a signature taking a
 * {@code ReservationId} or a {@code TabId} could not compile here. Billing does not need to know
 * what a reservation is — it needs to tell two folios apart and to refuse a tab where a stay is
 * expected, and {@link OwnerType} carries exactly that (decision #1).
 *
 * <p>The price of this shape is that a tab's id and a reservation's id are both {@code UUID} to the
 * compiler. The guard moved to runtime, which is why every method that cares states which
 * {@link OwnerType} it accepts.
 */
public record FolioOwner(OwnerType type, UUID value) {

    public FolioOwner {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
    }

    public static FolioOwner reservation(UUID value) {
        return new FolioOwner(OwnerType.RESERVATION, value);
    }

    public static FolioOwner tab(UUID value) {
        return new FolioOwner(OwnerType.TAB, value);
    }

    public boolean isReservation() {
        return type == OwnerType.RESERVATION;
    }

    public boolean isTab() {
        return type == OwnerType.TAB;
    }
}
