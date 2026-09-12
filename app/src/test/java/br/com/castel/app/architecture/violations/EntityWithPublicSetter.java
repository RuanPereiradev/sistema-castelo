package br.com.castel.app.architecture.violations;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.UUID;

/**
 * Fixture for B1: an {@code @Entity} must never expose a public setter.
 *
 * <p>Maps onto the real, already-migrated {@code property} table (declaring only its {@code id}
 * column) purely so that unrelated {@code @SpringBootTest} contexts, which build a Hibernate
 * SessionFactory over every {@code @Entity} under {@code br.com.castel.app} (test classes
 * included), can validate this fixture against a real table instead of failing to start.
 */
@Entity
@Table(name = "property")
public class EntityWithPublicSetter {

    @Id
    private UUID id;

    @Transient
    private String name;

    public void setName(String name) {
        this.name = name;
    }
}
