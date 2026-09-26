package br.com.castel.taxinvoice.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import br.com.castel.sharedkernel.Money;
import br.com.castel.taxinvoice.api.AccessKey;
import br.com.castel.taxinvoice.api.IssuedInvoice;
import br.com.castel.taxinvoice.api.TaxInvoiceRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class FakeTaxInvoiceIssuerTest {

    private static final Instant NOW = Instant.parse("2026-09-25T15:00:00Z");

    private final FakeTaxInvoiceIssuer issuer = new FakeTaxInvoiceIssuer(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void shouldIssueDistinctFakeKeysAtTheClockInstant() {
        IssuedInvoice first = issuer.issue(request());
        IssuedInvoice second = issuer.issue(request());

        assertThat(first.accessKey().value()).matches("FAKE-[0-9a-f]{32}");
        assertThat(second.accessKey()).isNotEqualTo(first.accessKey());
        assertThat(first.issuedAt()).isEqualTo(NOW);
        assertThat(first.documentUrl()).isEmpty();
    }

    @Test
    void shouldAcceptCancellingAnIssuedInvoiceTwice() {
        AccessKey accessKey = issuer.issue(request()).accessKey();

        assertThatCode(() -> {
                    issuer.cancel(accessKey, "Wrong amount");
                    issuer.cancel(accessKey, "Wrong amount");
                })
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectCancellingAnInvoiceItNeverIssued() {
        UnknownTaxInvoiceException failure = catchThrowableOfType(
                UnknownTaxInvoiceException.class, () -> issuer.cancel(new AccessKey("FAKE-unknown"), "Wrong amount"));

        assertThat(failure.code()).isEqualTo("TAX_INVOICE_NOT_FOUND");
    }

    private static TaxInvoiceRequest request() {
        return TaxInvoiceRequest.withoutPayerDocument(Money.of("180.00"), "Stay", "FOLIO-1");
    }
}
