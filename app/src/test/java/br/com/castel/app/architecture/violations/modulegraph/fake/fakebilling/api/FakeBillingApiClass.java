package br.com.castel.app.architecture.violations.modulegraph.fake.fakebilling.api;

import br.com.castel.app.architecture.violations.modulegraph.fake.fakebilling.domain.FakeBillingDomainClass;

/**
 * Fixture standing in for a module's api package that reaches into its own domain package, used to
 * prove that rule A6 actually fails when violated.
 */
public class FakeBillingApiClass {

    private final FakeBillingDomainClass internal = new FakeBillingDomainClass();

    public FakeBillingDomainClass internal() {
        return internal;
    }
}
