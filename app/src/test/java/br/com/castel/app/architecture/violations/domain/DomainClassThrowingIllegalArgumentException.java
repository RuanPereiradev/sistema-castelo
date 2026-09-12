package br.com.castel.app.architecture.violations.domain;

/**
 * Fixture for C5: a domain-scoped class must never throw {@link IllegalArgumentException}; it
 * should throw a specific {@code DomainException} with a stable code instead.
 */
public class DomainClassThrowingIllegalArgumentException {

    public void validate(String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }
}
