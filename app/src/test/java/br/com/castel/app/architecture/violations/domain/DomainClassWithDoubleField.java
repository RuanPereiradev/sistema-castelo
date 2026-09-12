package br.com.castel.app.architecture.violations.domain;

/**
 * Fixture for C2: a domain-scoped class must never hold a {@code double} or {@code float} field.
 */
public class DomainClassWithDoubleField {

    private double amount;
}
