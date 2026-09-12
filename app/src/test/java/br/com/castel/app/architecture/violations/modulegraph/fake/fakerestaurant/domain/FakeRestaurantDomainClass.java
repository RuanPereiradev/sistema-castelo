package br.com.castel.app.architecture.violations.modulegraph.fake.fakerestaurant.domain;

import br.com.castel.app.architecture.violations.modulegraph.fake.fakehotel.domain.FakeHotelDomainClass;

/**
 * Fixture for A2: "fakerestaurant" is not listed as allowed to depend on "fakehotel" at all in
 * the module graph (not even through its api package), so this fixture violates the allowed
 * dependency graph itself.
 */
public class FakeRestaurantDomainClass {

    private final FakeHotelDomainClass fakeHotelDomainClass = new FakeHotelDomainClass();
}
