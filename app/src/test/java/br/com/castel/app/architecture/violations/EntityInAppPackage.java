package br.com.castel.app.architecture.violations;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Fixture for D1: app is the composition root only, it must not hold JPA entities.
 *
 * <p>Maps onto the real, already-migrated {@code property} table (declaring only its {@code id}
 * column) purely so that unrelated {@code @SpringBootTest} contexts, which build a Hibernate
 * SessionFactory over every {@code @Entity} under {@code br.com.castel.app} (test classes
 * included), can validate this fixture against a real table instead of failing to start.
 */
@Entity
@Table(name = "property")
public class EntityInAppPackage {

    @Id
    private UUID id;
}
