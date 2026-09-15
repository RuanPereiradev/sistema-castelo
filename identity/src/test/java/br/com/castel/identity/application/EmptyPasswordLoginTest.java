package br.com.castel.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.castel.identity.api.Role;
import br.com.castel.identity.domain.User;
import br.com.castel.identity.domain.UserRepository;
import br.com.castel.identity.infra.JwtTokenIssuer;
import br.com.castel.sharedkernel.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Written from the "Tempo de resposta também precisa ser parecido" rule of
 * docs/task-0.4-identity-auth.md and the blocking finding of the final review
 * (docs/decisions/task-0.4.md): a null or empty password still spends one full BCrypt, in every
 * account situation, fails with {@code INVALID_CREDENTIALS}, counts in the limit and issues no token.
 *
 * <p>"Full BCrypt" is proven structurally by {@link ObservingPasswordEncoder#hashesComputed()}, which
 * only moves when {@code BCrypt.checkpw} runs. {@link EncoderDoubleDetectsSkippedHash} proves the
 * double does not move for an empty candidate, so the service tests are not tautological.
 */
class EmptyPasswordLoginTest {

    private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");
    private static final String SECRET = "test-only-jwt-secret-with-more-than-256-bits-0123456789abcdef";
    private static final String PASSWORD = "Empty-Canary-Password-42";
    private static final String CLIENT_IP = "198.51.100.7";

    enum Account {
        EXISTING_ACTIVE("joao.silva"),
        EXISTING_INACTIVE("maria.souza"),
        UNKNOWN_WELL_FORMED("ghost.user"),
        MALFORMED_USERNAME("Joao Silva");

        private final String username;

        Account(String username) {
            this.username = username;
        }
    }

    static Stream<Arguments> emptyPasswordsByAccount() {
        return Stream.of(Account.values())
                .flatMap(account -> Stream.of(Arguments.of(null, account), Arguments.of("", account)));
    }

    private MutableClock clock;
    private UserRepository repository;
    private RecordingLoginAttemptRateLimiter limiter;
    private ObservingPasswordEncoder encoder;
    private TokenIssuer tokenIssuer;
    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        repository = mock(UserRepository.class);
        limiter = RecordingLoginAttemptRateLimiter.inMemory(30, Duration.ofSeconds(5), clock);
        encoder = ObservingPasswordEncoder.observingSlots(limiter::isCurrentThreadInsideSlot);
        tokenIssuer = spy(new JwtTokenIssuer(SECRET, clock));
        service = new AuthenticationService(
                repository, encoder, tokenIssuer, limiter, TransactionOperations.withoutTransaction(), clock);
        when(repository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(User.class))).then(returnsFirstArg());
    }

    private void given(Account account) {
        if (account == Account.EXISTING_ACTIVE || account == Account.EXISTING_INACTIVE) {
            store(account);
        }
    }

    private void store(Account account) {
        User user = User.create(UUID.randomUUID(), account.username, "Stored User", PASSWORD, Set.of(Role.WAITER), encoder);
        if (account == Account.EXISTING_INACTIVE) {
            user.deactivate();
        }
        when(repository.findByUsername(user.username())).thenReturn(Optional.of(user));
        when(repository.findByIdForUpdate(user.id())).thenReturn(Optional.of(user));
    }

    @Nested
    class SpendsOneRealBcrypt {

        @ParameterizedTest(name = "password={0}, account={1}")
        @MethodSource("br.com.castel.identity.application.EmptyPasswordLoginTest#emptyPasswordsByAccount")
        void shouldCallPasswordComparisonExactlyOnceWhenPasswordIsNullOrEmpty(String password, Account account) {
            given(account);

            catchThrowable(() -> service.login(account.username, password, CLIENT_IP));

            assertThat(encoder.comparisons()).isOne();
        }

        @ParameterizedTest(name = "password={0}, account={1}")
        @MethodSource("br.com.castel.identity.application.EmptyPasswordLoginTest#emptyPasswordsByAccount")
        void shouldComputeExactlyOneBcryptHashWhenPasswordIsNullOrEmpty(String password, Account account) {
            given(account);

            catchThrowable(() -> service.login(account.username, password, CLIENT_IP));

            assertThat(encoder.hashesComputed()).isOne();
        }

        @ParameterizedTest(name = "password={0}, account={1}")
        @MethodSource("br.com.castel.identity.application.EmptyPasswordLoginTest#emptyPasswordsByAccount")
        void shouldHashNonEmptyCandidateWhenPasswordIsNullOrEmpty(String password, Account account) {
            given(account);

            catchThrowable(() -> service.login(account.username, password, CLIENT_IP));

            assertThat(encoder.hashedCandidates()).singleElement().asString().isNotEmpty();
        }

        @ParameterizedTest(name = "password={0}, account={1}")
        @MethodSource("br.com.castel.identity.application.EmptyPasswordLoginTest#emptyPasswordsByAccount")
        void shouldComputeHashOfNullOrEmptyPasswordInsidePasswordCheckSlot(String password, Account account) {
            given(account);

            catchThrowable(() -> service.login(account.username, password, CLIENT_IP));

            assertThat(encoder.comparisonsInsideSlot()).isOne();
        }
    }

    @Nested
    class FailsLikeAnyCredentialFailure {

        @ParameterizedTest(name = "password={0}, account={1}")
        @MethodSource("br.com.castel.identity.application.EmptyPasswordLoginTest#emptyPasswordsByAccount")
        void shouldRejectNullOrEmptyPasswordWithInvalidCredentials(String password, Account account) {
            given(account);

            assertThatThrownBy(() -> service.login(account.username, password, CLIENT_IP))
                    .asInstanceOf(type(InvalidCredentialsException.class))
                    .extracting(InvalidCredentialsException::code)
                    .isEqualTo("INVALID_CREDENTIALS");
        }

        @ParameterizedTest(name = "password={0}, account={1}")
        @MethodSource("br.com.castel.identity.application.EmptyPasswordLoginTest#emptyPasswordsByAccount")
        void shouldCountNullOrEmptyPasswordLoginAsFailure(String password, Account account) {
            given(account);

            catchThrowable(() -> service.login(account.username, password, CLIENT_IP));

            assertThat(limiter.recordedFailures()).isOne();
        }

        @ParameterizedTest(name = "password={0}, account={1}")
        @MethodSource("br.com.castel.identity.application.EmptyPasswordLoginTest#emptyPasswordsByAccount")
        void shouldNotIssueTokensWhenPasswordIsNullOrEmpty(String password, Account account) {
            given(account);

            catchThrowable(() -> service.login(account.username, password, CLIENT_IP));

            verify(tokenIssuer, never()).issueTokenPair(any());
        }

        @ParameterizedTest(name = "password={0}, account={1}")
        @MethodSource("br.com.castel.identity.application.EmptyPasswordLoginTest#emptyPasswordsByAccount")
        void shouldRaiseForNullOrEmptyPasswordTheSameExceptionAsForUnknownUserWithWrongPassword(
                String password, Account account) {
            Throwable unknownUser = catchThrowable(() -> service.login("nobody.here", "Wrong-Guess-Password-77", CLIENT_IP));
            given(account);

            Throwable emptyPassword = catchThrowable(() -> service.login(account.username, password, CLIENT_IP));

            assertThat(emptyPassword).hasSameClassAs(unknownUser).hasMessage(unknownUser.getMessage());
        }
    }

    /**
     * Control: without the substitution, the double would expose the bug. The delegate is asked to
     * compare an empty candidate and returns without hashing, so {@code hashesComputed} stays at zero
     * while {@code comparisons} still moves (which is why counting comparisons alone was tautological).
     */
    @Nested
    class EncoderDoubleDetectsSkippedHash {

        private String storedHash;

        @BeforeEach
        void encodeStoredHash() {
            storedHash = encoder.encode(PASSWORD);
        }

        @Test
        void shouldNotCountComputedHashWhenDelegateIsAskedToCompareEmptyPassword() {
            encoder.matches("", storedHash);

            assertThat(encoder.hashesComputed()).isZero();
        }

        @Test
        void shouldNotCountComputedHashWhenDelegateIsAskedToCompareNullPassword() {
            encoder.matches(null, storedHash);

            assertThat(encoder.hashesComputed()).isZero();
        }

        @Test
        void shouldStillCountComparisonWhenDelegateSkipsHashOfEmptyPassword() {
            encoder.matches("", storedHash);

            assertThat(encoder.comparisons()).isOne();
        }

        @Test
        void shouldNotCountComputedHashWhenStoredHashIsNotBcryptShaped() {
            encoder.matches(PASSWORD, "not-a-bcrypt-hash");

            assertThat(encoder.hashesComputed()).isZero();
        }

        @Test
        void shouldCountComputedHashWhenDelegateComparesNonEmptyPassword() {
            encoder.matches("any-non-empty-guess", storedHash);

            assertThat(encoder.hashesComputed()).isOne();
        }
    }
}
