package br.com.castel.app.config;

import br.com.castel.identity.api.CurrentUserProvider;
import br.com.castel.sharedkernel.AuditedEntity;
import br.com.castel.sharedkernel.AuditingListener;
import br.com.castel.sharedkernel.AuditorAware;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the audit columns of every {@link AuditedEntity}: who is writing, and from which clock the
 * timestamp comes.
 *
 * <p>{@link AuditingListener} is registered as a bean so the persistence provider takes this
 * instance, with its collaborators already injected, instead of building one by reflection. The
 * timestamp comes from the project's {@link Clock} bean, so audit dates stay as controllable in a
 * test as every other date in the system.
 *
 * <p>Spring Data JPA's {@code @EnableJpaAuditing} is deliberately not used: its annotations would
 * have to sit on the fields of {@link AuditedEntity}, in {@code shared-kernel}, which is not allowed
 * to see Spring (decision #30).
 */
@Configuration
public class AuditingConfig {

    @Bean
    public AuditorAware auditorAware(CurrentUserProvider currentUserProvider) {
        return new CurrentUserAuditorAware(currentUserProvider);
    }

    @Bean
    public AuditingListener auditingListener(AuditorAware auditorAware, Clock clock) {
        return new AuditingListener(auditorAware, clock);
    }
}
