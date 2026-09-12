package br.com.castel.app.architecture.violations;

import java.util.Date;

/**
 * Fixture for C1: {@code java.util.Date} must never be used; {@code java.time} replaces it.
 */
public class ClassUsingJavaUtilDate {

    private final Date createdAt = new Date();
}
