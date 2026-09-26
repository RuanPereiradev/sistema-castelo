package br.com.castel.billing.domain;

import br.com.castel.billing.api.FolioId;
import br.com.castel.billing.api.FolioOwner;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@link Folio}. Implemented in {@code billing.infra}. */
public interface FolioRepository {

    /** Reads a folio without locking it. */
    Optional<Folio> findById(FolioId id);

    /**
     * Reads a folio and locks its row until the transaction ends ({@code SELECT ... FOR UPDATE}).
     * Every write goes through here before deciding anything (invariant 25 of task 1.3): without it,
     * a charge posted while the folio closes would leave a closed folio with a balance.
     */
    Optional<Folio> findByIdForUpdate(FolioId id);

    /** The open stay folio of the property under this reference code, if any. */
    Optional<Folio> findOpenStayByReferenceCode(UUID propertyId, String referenceCode);

    /** Whether the owner already has a folio, open or closed. */
    boolean existsByOwner(FolioOwner owner);

    /** The folio holding a payment under this idempotency key, already trimmed. */
    Optional<FolioId> findPaymentByIdempotencyKey(String idempotencyKey);

    /**
     * Writes the folio and its new charges and payments at once.
     *
     * <p>The database holds the rules about the set of folios, so a race the use case could not see
     * surfaces here and answers its own code rather than a constraint violation.
     *
     * @throws FolioAlreadyOpenedForOwnerException if another folio of the same owner was written first
     * @throws FolioReferenceAlreadyInUseException if another open stay took the reference code first
     * @throws IdempotencyKeyReusedException if another folio took the idempotency key first
     * @throws ChargeAlreadyReversedException if another reversal of the same charge was written first
     */
    Folio save(Folio folio);
}
