package br.com.castel.identity.infra;

import br.com.castel.identity.api.Role;
import br.com.castel.identity.domain.User;
import br.com.castel.identity.domain.UserRepository;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates one user per role in {@code dev}, plus an inactive one, all sharing the password from
 * {@code castel.dev.seed-password}: {@code admin} (ADMIN), {@code recepcao} (FRONT_DESK),
 * {@code garcom} (WAITER), {@code cozinha} (KITCHEN) and {@code inativo} (WAITER, deactivated).
 *
 * <p>Idempotent: a username that already exists is left untouched. There is no {@code Property}
 * aggregate yet, so a minimal row is inserted through {@link JdbcTemplate}, just enough to satisfy
 * {@code app_user}'s foreign key. Restricted to the {@code dev} profile and to
 * {@code castel.dev.seed-enabled=true}; it must never run in {@code prod}.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "castel.dev.seed-enabled", havingValue = "true")
public class DevUserSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String seedPassword;

    public DevUserSeeder(
            UserRepository userRepository,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            @Value("${castel.dev.seed-password}") String seedPassword) {
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.seedPassword = seedPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        UUID propertyId = findOrCreateProperty();
        seedActiveUser(propertyId, "admin", "Dev Admin", Role.ADMIN);
        seedActiveUser(propertyId, "recepcao", "Dev Front Desk", Role.FRONT_DESK);
        seedActiveUser(propertyId, "garcom", "Dev Waiter", Role.WAITER);
        seedActiveUser(propertyId, "cozinha", "Dev Kitchen", Role.KITCHEN);
        seedInactiveUser(propertyId, "inativo", "Dev Inactive Waiter", Role.WAITER);
    }

    private void seedActiveUser(UUID propertyId, String username, String fullName, Role role) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }
        userRepository.save(newUser(propertyId, username, fullName, role));
    }

    private void seedInactiveUser(UUID propertyId, String username, String fullName, Role role) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }
        User user = newUser(propertyId, username, fullName, role);
        user.deactivate();
        userRepository.save(user);
    }

    private User newUser(UUID propertyId, String username, String fullName, Role role) {
        return User.create(propertyId, username, fullName, seedPassword, Set.of(role), passwordEncoder);
    }

    private UUID findOrCreateProperty() {
        var existing = jdbcTemplate.queryForList("SELECT id FROM property LIMIT 1", UUID.class);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        UUID propertyId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO property (id, legal_name, time_zone) VALUES (?, ?, ?)",
                propertyId,
                "Castel Dev",
                "America/Fortaleza");
        return propertyId;
    }
}
