package br.com.castel.billing.application;

import br.com.castel.billing.api.ChargeId;
import br.com.castel.billing.api.ChargeRequest;
import br.com.castel.billing.api.ChargeView;
import br.com.castel.billing.api.FolioFacade;
import br.com.castel.billing.api.FolioId;
import br.com.castel.billing.api.FolioOwner;
import br.com.castel.billing.api.FolioReference;
import br.com.castel.billing.api.FolioView;
import br.com.castel.billing.domain.Charge;
import br.com.castel.billing.domain.Folio;
import br.com.castel.billing.domain.FolioAlreadyOpenedForOwnerException;
import br.com.castel.billing.domain.FolioNotFoundException;
import br.com.castel.billing.domain.FolioReferenceAlreadyInUseException;
import br.com.castel.billing.domain.FolioRepository;
import br.com.castel.billing.domain.IdempotencyKeyReusedException;
import br.com.castel.billing.domain.Payment;
import br.com.castel.billing.domain.PaymentId;
import br.com.castel.billing.domain.PaymentMethod;
import br.com.castel.sharedkernel.AuditorAware;
import br.com.castel.sharedkernel.CurrentProperty;
import br.com.castel.sharedkernel.Money;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The folio's use cases: the {@link FolioFacade} other modules call, and the routes of the front desk.
 *
 * <p>Every write loads the folio with {@link FolioRepository#findByIdForUpdate}, so two writes on the
 * same folio run one after the other (invariant 25 of task 1.3, decision #15). Reads do not lock.
 * The operator comes from {@link AuditorAware} and the moment from the {@link Clock}.
 *
 * <p>The rules about the set of folios — one per owner, one open stay per reference code, an
 * idempotency key held by one folio — are checked here to answer a readable code, and held by the
 * database against a race; the repository translates a lost race into the same code.
 */
@Service
public class FolioService implements FolioFacade {

    private final FolioRepository folios;
    private final CurrentProperty currentProperty;
    private final AuditorAware auditorAware;
    private final Clock clock;

    public FolioService(
            FolioRepository folios, CurrentProperty currentProperty, AuditorAware auditorAware, Clock clock) {
        this.folios = folios;
        this.currentProperty = currentProperty;
        this.auditorAware = auditorAware;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ facade

    /**
     * @throws FolioAlreadyOpenedForOwnerException if the reservation already has a folio
     * @throws FolioReferenceAlreadyInUseException if another open stay uses the reference code
     */
    @Override
    @Transactional
    public FolioId openStayFolio(FolioOwner reservation, FolioReference reference) {
        Folio folio = Folio.openForStay(currentProperty.id(), reservation, reference, clock.instant());
        rejectSecondFolioOf(folio.owner());
        rejectReferenceUsedByAnother(folio);
        return folios.save(folio).id();
    }

    /** @throws FolioAlreadyOpenedForOwnerException if the tab already has a folio */
    @Override
    @Transactional
    public FolioId openTabFolio(FolioOwner tab) {
        Folio folio = Folio.openForTab(currentProperty.id(), tab, clock.instant());
        rejectSecondFolioOf(folio.owner());
        return folios.save(folio).id();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FolioView> findOpenStayFolioByCode(String code) {
        return findOpenStay(code).map(FolioService::view);
    }

    /** @throws FolioReferenceAlreadyInUseException if another open stay uses the new code */
    @Override
    @Transactional
    public void changeReference(FolioId folioId, FolioReference newReference) {
        Folio folio = loadForUpdate(folioId);
        folio.changeReference(newReference);
        rejectReferenceUsedByAnother(folio);
        folios.save(folio);
    }

    @Override
    @Transactional
    public ChargeId post(FolioId folioId, ChargeRequest charge) {
        Folio folio = loadForUpdate(folioId);
        Charge posted = folio.post(charge);
        saveReadable(folio);
        return posted.id();
    }

    @Override
    @Transactional
    public ChargeId reverse(FolioId folioId, ChargeId chargeId, String reason) {
        Folio folio = loadForUpdate(folioId);
        Charge reversal = folio.reverse(chargeId, reason);
        saveReadable(folio);
        return reversal.id();
    }

    @Override
    @Transactional(readOnly = true)
    public Money balanceOf(FolioId folioId) {
        return load(folioId).balance();
    }

    @Override
    @Transactional(readOnly = true)
    public FolioView findById(FolioId folioId) {
        return view(load(folioId));
    }

    @Override
    @Transactional
    public void close(FolioId folioId) {
        closeFolio(folioId);
    }

    // ------------------------------------------------------------------ front desk

    /** @throws FolioNotFoundException if the folio does not exist */
    @Transactional(readOnly = true)
    public Folio find(FolioId folioId) {
        return load(folioId);
    }

    /** @throws FolioNotFoundException if no open stay folio answers the code */
    @Transactional(readOnly = true)
    public Folio findOpenStayByReferenceCode(String referenceCode) {
        return findOpenStay(referenceCode)
                .orElseThrow(() -> new FolioNotFoundException("No open stay folio answers this reference code"));
    }

    /**
     * Registers a payment at the counter, or answers the one already registered under the key when
     * this is a retry (decision #5 of task 1.3). The key is checked against other folios here; a
     * retry on the same folio is recognised by the folio itself.
     *
     * @throws IdempotencyKeyReusedException if another folio holds the key
     */
    @Transactional
    public ReceivedPayment receivePayment(FolioId folioId, PaymentMethod method, Money amount, String idempotencyKey) {
        String key = Payment.requireValidIdempotencyKey(idempotencyKey);
        Folio folio = loadForUpdate(folioId);
        rejectKeyHeldByAnother(key, folio);
        Payment payment = folio.receive(method, amount, key, auditorAware.currentAuditorId(), clock.instant());
        folios.save(folio);
        return new ReceivedPayment(payment, folio.balance());
    }

    @Transactional
    public Folio refundPayment(FolioId folioId, PaymentId paymentId, String reason) {
        Folio folio = loadForUpdate(folioId);
        folio.refund(paymentId, reason, auditorAware.currentAuditorId(), clock.instant());
        return saveReadable(folio);
    }

    /** The authenticated user authorizes the adjustment; the route guarantees an {@code ADMIN}. */
    @Transactional
    public Folio postAdjustment(FolioId folioId, Money amount, String description, String reason) {
        Folio folio = loadForUpdate(folioId);
        folio.postAdjustment(amount, description, reason, auditorAware.currentAuditorId());
        return saveReadable(folio);
    }

    @Transactional
    public Folio reverseCharge(FolioId folioId, ChargeId chargeId, String reason) {
        Folio folio = loadForUpdate(folioId);
        folio.reverse(chargeId, reason);
        return saveReadable(folio);
    }

    @Transactional
    public Folio closeFolio(FolioId folioId) {
        Folio folio = loadForUpdate(folioId);
        folio.close(auditorAware.currentAuditorId(), clock.instant());
        return saveReadable(folio);
    }

    // ------------------------------------------------------------------ internals

    private Folio load(FolioId folioId) {
        return folios.findById(folioId).orElseThrow(() -> notFound(folioId));
    }

    private Folio loadForUpdate(FolioId folioId) {
        return folios.findByIdForUpdate(folioId).orElseThrow(() -> notFound(folioId));
    }

    /**
     * Reads the totals before saving, inside the transaction. A write that takes a total or the
     * balance out of the range of {@link Money} fails here with {@code MONEY_OUT_OF_RANGE} and rolls
     * back, instead of committing a folio that no read could answer any more. The answer built from
     * the returned folio after the commit reads the same totals.
     */
    private Folio saveReadable(Folio folio) {
        folio.balance();
        return folios.save(folio);
    }

    private Optional<Folio> findOpenStay(String referenceCode) {
        return Optional.ofNullable(referenceCode)
                .map(String::trim)
                .flatMap(code -> folios.findOpenStayByReferenceCode(currentProperty.id(), code));
    }

    /** One folio per owner (invariant 3); a second one is refused, not answered with the first (#18). */
    private void rejectSecondFolioOf(FolioOwner owner) {
        if (folios.existsByOwner(owner)) {
            throw new FolioAlreadyOpenedForOwnerException(
                    "The " + owner.type() + " " + owner.value() + " already has a folio");
        }
    }

    /** One open stay per reference code (decision #17). The folio itself does not compete. */
    private void rejectReferenceUsedByAnother(Folio folio) {
        String code = folio.reference().orElseThrow().code();
        Optional<Folio> holder = folios.findOpenStayByReferenceCode(folio.propertyId(), code);
        if (holder.filter(other -> !other.id().equals(folio.id())).isPresent()) {
            throw new FolioReferenceAlreadyInUseException();
        }
    }

    /** An idempotency key belongs to one folio (decision #16); the same folio decides a retry itself. */
    private void rejectKeyHeldByAnother(String key, Folio folio) {
        Optional<FolioId> holder = folios.findPaymentByIdempotencyKey(key);
        if (holder.filter(other -> !other.equals(folio.id())).isPresent()) {
            throw new IdempotencyKeyReusedException("The idempotency key was used on another folio");
        }
    }

    private static FolioNotFoundException notFound(FolioId folioId) {
        return new FolioNotFoundException("No folio " + folioId.value());
    }

    private static FolioView view(Folio folio) {
        return new FolioView(
                folio.id(),
                folio.type(),
                folio.status(),
                folio.owner(),
                folio.reference(),
                folio.charges().stream().map(FolioService::view).toList(),
                folio.balance());
    }

    private static ChargeView view(Charge charge) {
        return new ChargeView(
                charge.id(),
                charge.amount(),
                charge.description(),
                charge.createdAt(),
                charge.reversalOf().orElse(null));
    }
}
