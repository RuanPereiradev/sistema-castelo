package br.com.castel.app.architecture.violations.modulegraph.fake.fakesharedkernel;

import br.com.castel.app.architecture.violations.modulegraph.fake.fakehotel.domain.FakeHotelDomainClass;

/**
 * Fixture for A3: a shared-kernel analog must never depend on any other module of the project.
 */
public class FakeSharedKernelDependingOnOtherModule {

    private final FakeHotelDomainClass fakeHotelDomainClass = new FakeHotelDomainClass();
}
