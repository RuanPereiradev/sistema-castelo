package br.com.castel.app.architecture.violations.modulegraph.fake.fakeidentity.application;

import br.com.castel.app.architecture.violations.modulegraph.fake.fakeidentity.infra.FakeIdentityInfraClass;

/**
 * Fixture for A5: an application class importing the infra class of its own module instead of a
 * port. Same module, so the module boundary rules (A1, A2) do not catch it.
 */
public class FakeIdentityApplicationClass {

    private final FakeIdentityInfraClass fakeIdentityInfraClass = new FakeIdentityInfraClass();
}
