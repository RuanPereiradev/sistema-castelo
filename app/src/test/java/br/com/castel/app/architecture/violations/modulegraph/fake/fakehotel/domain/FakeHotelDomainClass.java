package br.com.castel.app.architecture.violations.modulegraph.fake.fakehotel.domain;

import br.com.castel.app.architecture.violations.modulegraph.fake.fakebilling.domain.FakeBillingDomainClass;

/**
 * Fixture for A1: a module reaching directly into another module's domain package instead of
 * going through its api package. "fakehotel" is allowed to depend on "fakebilling" at the
 * module-graph level (A2), so this fixture violates only the layer restriction (A1).
 */
public class FakeHotelDomainClass {

    private final FakeBillingDomainClass fakeBillingDomainClass = new FakeBillingDomainClass();
}
