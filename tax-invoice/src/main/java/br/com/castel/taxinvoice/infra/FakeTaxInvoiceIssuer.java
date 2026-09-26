package br.com.castel.taxinvoice.infra;

import br.com.castel.taxinvoice.api.AccessKey;
import br.com.castel.taxinvoice.api.IssuedInvoice;
import br.com.castel.taxinvoice.api.TaxInvoiceIssuer;
import br.com.castel.taxinvoice.api.TaxInvoiceRequest;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * An issuer that reaches no tax authority, active under {@code castel.tax-invoice.issuer=fake}.
 *
 * <p>Access keys are {@code FAKE-} followed by the 32 hex digits of a UUID. They deliberately do not
 * imitate the 44 digits of an NFC-e, so a fake key can never be read as a real invoice on a receipt.
 * Cancelling an issued key is accepted, and accepted again silently; cancelling an unknown one is
 * refused. State lives in memory and is lost on restart.
 */
@Component
@ConditionalOnProperty(name = "castel.tax-invoice.issuer", havingValue = "fake")
public class FakeTaxInvoiceIssuer implements TaxInvoiceIssuer {

    private static final Logger LOGGER = LoggerFactory.getLogger(FakeTaxInvoiceIssuer.class);

    private static final String ACCESS_KEY_PREFIX = "FAKE-";

    private final Clock clock;
    private final Set<AccessKey> issuedKeys = ConcurrentHashMap.newKeySet();

    public FakeTaxInvoiceIssuer(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        LOGGER.warn("Fake TaxInvoiceIssuer active: no tax invoice reaches the tax authority");
    }

    @Override
    public IssuedInvoice issue(TaxInvoiceRequest request) {
        Objects.requireNonNull(request, "request");
        AccessKey accessKey = new AccessKey(ACCESS_KEY_PREFIX + UUID.randomUUID().toString().replace("-", ""));
        issuedKeys.add(accessKey);
        return new IssuedInvoice(accessKey, clock.instant(), Optional.empty());
    }

    @Override
    public void cancel(AccessKey accessKey, String reason) {
        Objects.requireNonNull(accessKey, "accessKey");
        Objects.requireNonNull(reason, "reason");
        if (!issuedKeys.contains(accessKey)) {
            throw new UnknownTaxInvoiceException(accessKey);
        }
    }
}
