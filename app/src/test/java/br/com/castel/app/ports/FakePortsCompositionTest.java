package br.com.castel.app.ports;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.app.support.AbstractIntegrationTest;
import br.com.castel.payment.api.PaymentProcessor;
import br.com.castel.payment.infra.FakePaymentProcessor;
import br.com.castel.taxinvoice.api.TaxInvoiceIssuer;
import br.com.castel.taxinvoice.infra.FakeTaxInvoiceIssuer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** The base configuration wires the fake adapters behind both ports (task 1.4, decision #1). */
@SpringBootTest
class FakePortsCompositionTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentProcessor paymentProcessor;

    @Autowired
    private TaxInvoiceIssuer taxInvoiceIssuer;

    @Test
    void shouldWireTheFakeAdaptersBehindBothPorts() {
        assertThat(paymentProcessor).isInstanceOf(FakePaymentProcessor.class);
        assertThat(taxInvoiceIssuer).isInstanceOf(FakeTaxInvoiceIssuer.class);
    }
}
