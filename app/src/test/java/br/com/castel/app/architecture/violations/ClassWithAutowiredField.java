package br.com.castel.app.architecture.violations;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * Fixture for B2: dependencies must be injected through the constructor, never a field.
 */
public class ClassWithAutowiredField {

    @Autowired
    private String someCollaborator;
}
