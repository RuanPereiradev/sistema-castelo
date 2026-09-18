package br.com.castel.app.audit;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.app.support.AbstractIntegrationTest;
import br.com.castel.identity.api.AuthenticatedUser;
import br.com.castel.identity.api.CurrentUserProvider;
import br.com.castel.identity.api.Role;
import br.com.castel.identity.api.UserId;
import br.com.castel.identity.domain.User;
import br.com.castel.identity.domain.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Auditing invariants of docs/task-0.5b-cross-cutting-foundation.md, items 5 and 6.7, over the real
 * database: {@code created_by} never changes after the insert, {@code updated_at}/{@code updated_by}
 * change on every update, and with no authenticated user the author is the reserved system UUID.
 *
 * <p>No test entity is created. The task makes {@code app_user} audited (decision #5), so the
 * assertions run over {@code app_user}, the only audited table that already has an aggregate.
 * A test {@code @Entity} under {@code br.com.castel.app} would be swept by the component scan of
 * {@code CastelApplication} and break {@code ddl-auto: validate} in every other integration test.
 *
 * <p>The authenticated user is switched through the {@link CurrentUserProvider} port of
 * {@code identity/api}, which is what the {@code AuditorAware} of the task reads.
 */
@SpringBootTest
@Import(JpaAuditingIntegrationTest.SwitchableCurrentUserConfiguration.class)
class JpaAuditingIntegrationTest extends AbstractIntegrationTest {

    private static final UUID PROPERTY_ID = UUID.fromString("0b7e3b8e-3c52-4c1e-9d0e-4a1f00000006");
    private static final String PASSWORD = "Auditing-Password-42";

    static class SwitchableCurrentUserProvider implements CurrentUserProvider {

        private final AtomicReference<AuthenticatedUser> current = new AtomicReference<>();

        @Override
        public Optional<AuthenticatedUser> currentUser() {
            return Optional.ofNullable(current.get());
        }

        void authenticate(AuthenticatedUser user) {
            current.set(user);
        }

        void clear() {
            current.set(null);
        }
    }

    @TestConfiguration
    static class SwitchableCurrentUserConfiguration {

        @Bean
        @Primary
        SwitchableCurrentUserProvider switchableCurrentUserProvider() {
            return new SwitchableCurrentUserProvider();
        }
    }

    @Autowired
    private SwitchableCurrentUserProvider currentUserProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void createPropertyAndClearUser() {
        jdbcTemplate.update(
                "insert into property (id, legal_name) values (?, ?) on conflict (id) do nothing",
                PROPERTY_ID,
                "Auditing Property");
        currentUserProvider.clear();
    }

    // ------------------------------------------------------------- fixtures

    private record AuditColumns(UUID createdBy, Instant createdAt, UUID updatedBy, Instant updatedAt) {}

    private UserId insertUser() {
        String username = "aud_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return transactionTemplate
                .execute(status -> userRepository
                        .save(User.create(PROPERTY_ID, username, "Auditing User", PASSWORD, Set.of(Role.WAITER), passwordEncoder))
                        .id());
    }

    private void deactivate(UserId userId) {
        transactionTemplate.executeWithoutResult(status -> {
            User user = userRepository.findById(userId).orElseThrow();
            user.deactivate();
            userRepository.save(user);
        });
    }

    private void activate(UserId userId) {
        transactionTemplate.executeWithoutResult(status -> {
            User user = userRepository.findById(userId).orElseThrow();
            user.activate();
            userRepository.save(user);
        });
    }

    private AuditColumns auditColumnsOf(UserId userId) {
        return jdbcTemplate.queryForObject(
                "select created_by, created_at, updated_by, updated_at from app_user where id = ?",
                (row, number) -> new AuditColumns(
                        row.getObject("created_by", UUID.class),
                        instantOf(row.getTimestamp("created_at")),
                        row.getObject("updated_by", UUID.class),
                        instantOf(row.getTimestamp("updated_at"))),
                userId.value());
    }

    private static Instant instantOf(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static AuthenticatedUser authenticated(UserId id) {
        return new AuthenticatedUser(id, "operator", Set.of(Role.ADMIN));
    }

    private static void pauseSoTimestampsDiffer() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------- author with no authenticated user

    @Test
    void shouldStampAnAuthorEvenWithoutAuthenticatedUser() {
        UserId userId = insertUser();

        assertThat(auditColumnsOf(userId).createdBy()).isNotNull();
    }

    @Test
    void shouldStampTheSameSystemAuthorOnEveryInsertWithoutAuthenticatedUser() {
        UUID firstAuthor = auditColumnsOf(insertUser()).createdBy();
        UUID secondAuthor = auditColumnsOf(insertUser()).createdBy();

        assertThat(firstAuthor).isEqualTo(secondAuthor);
    }

    @Test
    void shouldNotUseAnyRealUserIdAsSystemAuthor() {
        UserId operator = insertUser();
        currentUserProvider.authenticate(authenticated(operator));
        UUID authoredByOperator = auditColumnsOf(insertUser()).createdBy();
        currentUserProvider.clear();

        UUID systemAuthor = auditColumnsOf(insertUser()).createdBy();

        assertThat(systemAuthor).isNotEqualTo(authoredByOperator);
    }

    @Test
    void shouldStampSystemAuthorAsUpdaterWithoutAuthenticatedUser() {
        UserId userId = insertUser();

        deactivate(userId);

        assertThat(auditColumnsOf(userId).updatedBy()).isNotNull();
    }

    // ------------------------------------------------------------- author with authenticated user

    @Test
    void shouldStampAuthenticatedUserAsAuthorOnInsert() {
        UserId operator = insertUser();
        currentUserProvider.authenticate(authenticated(operator));

        UserId inserted = insertUser();

        assertThat(auditColumnsOf(inserted).createdBy()).isEqualTo(operator.value());
    }

    @Test
    void shouldStampAuthenticatedUserAsUpdaterOnUpdate() {
        UserId operator = insertUser();
        UserId userId = insertUser();
        currentUserProvider.authenticate(authenticated(operator));

        deactivate(userId);

        assertThat(auditColumnsOf(userId).updatedBy()).isEqualTo(operator.value());
    }

    // ------------------------------------------------------------- created never changes

    @Test
    void shouldKeepCreatedByUnchangedOnUpdate() {
        UserId userId = insertUser();
        UUID createdBy = auditColumnsOf(userId).createdBy();
        currentUserProvider.authenticate(authenticated(insertUser()));

        deactivate(userId);

        assertThat(auditColumnsOf(userId).createdBy()).isEqualTo(createdBy);
    }

    @Test
    void shouldKeepCreatedAtUnchangedOnUpdate() {
        UserId userId = insertUser();
        Instant createdAt = auditColumnsOf(userId).createdAt();
        pauseSoTimestampsDiffer();

        deactivate(userId);

        assertThat(auditColumnsOf(userId).createdAt()).isEqualTo(createdAt);
    }

    @Test
    void shouldStampCreatedAtOnInsert() {
        UserId userId = insertUser();

        assertThat(auditColumnsOf(userId).createdAt()).isNotNull();
    }

    // ------------------------------------------------------------- update always stamps

    @Test
    void shouldStampUpdatedAtOnUpdate() {
        UserId userId = insertUser();

        deactivate(userId);

        assertThat(auditColumnsOf(userId).updatedAt()).isNotNull();
    }

    @Test
    void shouldAdvanceUpdatedAtOnEverySubsequentUpdate() {
        UserId userId = insertUser();
        deactivate(userId);
        Instant afterFirstUpdate = auditColumnsOf(userId).updatedAt();
        pauseSoTimestampsDiffer();

        activate(userId);

        assertThat(auditColumnsOf(userId).updatedAt()).isAfter(afterFirstUpdate);
    }

    @Test
    void shouldChangeUpdatedByWhenAnotherUserUpdatesTheSameRow() {
        UserId userId = insertUser();
        UserId firstOperator = insertUser();
        UserId secondOperator = insertUser();
        currentUserProvider.authenticate(authenticated(firstOperator));
        deactivate(userId);
        currentUserProvider.authenticate(authenticated(secondOperator));

        activate(userId);

        assertThat(auditColumnsOf(userId).updatedBy()).isEqualTo(secondOperator.value());
    }

    @Test
    void shouldNeverStampNullAuthorOnUpdate() {
        UserId userId = insertUser();
        currentUserProvider.clear();

        deactivate(userId);

        assertThat(auditColumnsOf(userId).updatedBy()).isNotNull();
    }
}
