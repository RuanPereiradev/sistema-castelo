package br.com.castel.billing.api;

import br.com.castel.sharedkernel.Money;
import java.util.Optional;

/**
 * Everything another module may ask of the billing module.
 *
 * <p>This is the bridge the whole system turns on: a tab closed against a room becomes a charge on
 * the stay's folio, and check-out gathers room nights and consumption into one account. The call is
 * synchronous and transactional — there must never be a closed tab without its charge.
 *
 * <p>The restaurant does not know what a reservation is and billing does not know what a room is.
 * What crosses is a {@link FolioOwner} and a {@link FolioReference}: an opaque identifier and a
 * short code an operator can type.
 */
public interface FolioFacade {

    /**
     * Opens the account of a stay.
     *
     * @param reservation the reservation this folio answers for; must be {@link OwnerType#RESERVATION}
     * @param reference how an operator finds it, whose {@code code} is the room number (decision #2)
     */
    FolioId openStayFolio(FolioOwner reservation, FolioReference reference);

    /**
     * Opens the account of a tab of someone who is not staying at the hotel.
     *
     * @param tab the tab this folio answers for; must be {@link OwnerType#TAB}
     */
    FolioId openTabFolio(FolioOwner tab);

    /**
     * Finds the open folio of a stay by what the guest says out loud: the room number.
     *
     * <p>Only an open folio answers, which is what keeps the room of a stay that already checked out
     * from being found by the next guest of the same room (decision #8).
     */
    Optional<FolioView> findOpenStayFolioByCode(String code);

    /**
     * Points a folio at another code, when the guest changes room mid-stay.
     *
     * <p>The account follows the guest: the charges posted while the old room was the reference stay
     * on this same folio, and the old room is left with no open account (decision #8).
     */
    void changeReference(FolioId folioId, FolioReference newReference);

    /**
     * Writes a posting on a folio.
     *
     * @return the id of the charge that was written
     */
    ChargeId post(FolioId folioId, ChargeRequest charge);

    /**
     * Undoes a posting, by writing the opposing one.
     *
     * <p>The original charge stays on the folio: a bill that silently loses a line is a bill nobody
     * can explain to the guest. The reason travels because a reversal without one is indefensible at
     * the counter (decision #5).
     *
     * @return the id of the opposing charge
     */
    ChargeId reverse(FolioId folioId, ChargeId chargeId, String reason);

    /** What the folio still owes: the sum of its charges minus the sum of its payments. */
    Money balanceOf(FolioId folioId);

    FolioView findById(FolioId folioId);
}
