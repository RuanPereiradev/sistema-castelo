package br.com.castel.billing.api;

import java.util.Objects;

/**
 * How an operator finds and recognises a folio: a short code to type and a label to read.
 *
 * <p>For a stay, the code is the room number — what the guest knows by heart and says out loud at
 * the restaurant (decision #2), written by the hotel module when the room is assigned. On a room
 * change the code becomes the new room and the old one is left with no open account, because the
 * account belongs to the stay, not to the room (decision #8).
 *
 * <p>Billing never interprets the code. It stores what the owning module wrote and matches it, which
 * is what keeps a module that does not know what a room is able to answer "which folio is room 102".
 */
public record FolioReference(String code, String label) {

    public FolioReference {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(label, "label");
    }
}
