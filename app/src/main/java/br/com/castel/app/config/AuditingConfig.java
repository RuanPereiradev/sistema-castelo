package br.com.castel.app.config;

import br.com.castel.identity.api.CurrentUserProvider;
import java.time.Clock;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import java.util.UUID;

/**
 * Turns on the four audit columns for every entity annotated with
 * {@code @EntityListeners(AuditingEntityListener.class)}.
 *
 * <p>The timestamp comes from the project's {@link Clock} bean instead of Spring Data's internal
 * clock, so audit dates are as controllable in a test as every other date in the system.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditingDateTimeProvider")
public class AuditingConfig {

    @Bean
    public AuditorAware<UUID> auditorAware(CurrentUserProvider currentUserProvider) {
        return new CurrentUserAuditorAware(currentUserProvider);
    }

    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }
}
