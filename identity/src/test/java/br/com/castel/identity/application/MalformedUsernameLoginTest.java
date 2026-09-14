package br.com.castel.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Written from the "Username validado antes de tudo" rule of docs/task-0.4-identity-auth.md and
 * decision #62: a username outside {@code [a-z0-9._]} with 3 to 30 characters, after lowercasing,
 * is an unknown user right away, without any repository query, with the dummy BCrypt inside the
 * slot and the same {@code INVALID_CREDENTIALS}. All malformed usernames of an IP share one pair.
 */
class MalformedUsernameLoginTest {

    private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");
    private static final String SECRET = "test-only-jwt-secret-with-more-than-256-bits-0123456789abcdef";
    private static final String PASSWORD = "Malformed-Canary-Password-42";
    private static final String WRONG_PASSWORD = "Malformed-Wrong-Guess-77";
    private static final String CLIENT_IP = "198.51.100.7";
    private static final String UNICODE_VARIANT_OF_COZINHA = "cozİnha";

    static Stream<String> malformedUsernames() {
        return Stream.of(UNICODE_VARIANT_OF_COZINHA, "Joao Silva", "ab", "a".repeat(31), "a\nb", "");
    }

    private MutableClock clock;
    private UserRepository repository;
    private RecordingLoginAttemptRateLimiter limiter;
    private ObservingPasswordEncoder encoder;
    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        repository = mock(UserRepository.class);
        limiter = RecordingLoginAttemptRateLimiter.inMemory(30, Duration.ofSeconds(5), clock);
        encoder = ObservingPasswordEncoder.observingSlots(limiter::isCurrentThreadInsideSlot);
        service = new AuthenticationService(
                repository, encoder, new JwtTokenIssuer(SECRET, clock), limiter,
                TransactionOperations.withoutTransaction(), clock);
    }

    private User storedUser(String username) {
        User user = User.create(UUID.randomUUID(), username, "Stored User", PASSWORD, Set.of(Role.KITCHEN), encoder);
        when(repository.findByUsername(user.username())).thenReturn(Optional.of(user));
        when(repository.findByIdForUpdate(user.id())).thenReturn(Optional.of(user));
        when(repository.save(any(User.class))).then(returnsFirstArg());
        return user;
    }

    /** Distinct malformed usernames: a space is never allowed. */
    private void failLoginsOnDistinctMalformedUsernames(int count) {
        IntStream.range(0, count)
                .forEach(attempt -> catchThrowable(() -> service.login("user " + attempt, WRONG_PASSWORD, CLIENT_IP)));
    }

    @Nested
    class TreatedAsUnknownUserBeforeAnyQuery {

        @ParameterizedTest
        @MethodSource("br.com.castel.identity.application.MalformedUsernameLoginTest#malformedUsernames")
        void shouldNotTouchUserRepositoryForMalformedUsername(String malformedUsername) {
            catchThrowable(() -> service.login(malformedUsername, PASSWORD, CLIENT_IP));

            verifyNoInteractions(repository);
        }

        @ParameterizedTest
        @MethodSource("br.com.castel.identity.application.MalformedUsernameLoginTest#malformedUsernames")
        void shouldRejectMalformedUsernameWithInvalidCredentials(String malformedUsername) {
            assertThatThrownBy(() -> service.login(malformedUsername, PASSWORD, CLIENT_IP))
                    .asInstanceOf(type(InvalidCredentialsException.class))
                    .extracting(InvalidCredentialsException::code)
                    .isEqualTo("INVALID_CREDENTIALS");
        }

        @ParameterizedTest
        @MethodSource("br.com.castel.identity.application.MalformedUsernameLoginTest#malformedUsernames")
        void shouldSpendExactlyOneDummyPasswordComparisonForMalformedUsername(String malformedUsername) {
            catchThrowable(() -> service.login(malformedUsername, PASSWORD, CLIENT_IP));

            assertThat(encoder.comparisons()).isOne();
        }

        @ParameterizedTest
        @MethodSource("br.com.castel.identity.application.MalformedUsernameLoginTest#malformedUsernames")
        void shouldComputeExactlyOneDummyBcryptHashForMalformedUsername(String malformedUsername) {
            catchThrowable(() -> service.login(malformedUsername, PASSWORD, CLIENT_IP));

            assertThat(encoder.hashesComputed()).isOne();
        }

        @ParameterizedTest
        @MethodSource("br.com.castel.identity.application.MalformedUsernameLoginTest#malformedUsernames")
        void shouldSpendDummyComparisonOfMalformedUsernameInsidePasswordCheckSlot(String malformedUsername) {
            catchThrowable(() -> service.login(malformedUsername, PASSWORD, CLIENT_IP));

            assertThat(encoder.comparisonsInsideSlot()).isOne();
        }

        @ParameterizedTest
        @MethodSource("br.com.castel.identity.application.MalformedUsernameLoginTest#malformedUsernames")
        void shouldCountMalformedUsernameAttemptAsFailure(String malformedUsername) {
            catchThrowable(() -> service.login(malformedUsername, PASSWORD, CLIENT_IP));

            assertThat(limiter.recordedFailures()).isOne();
        }

        @ParameterizedTest
        @MethodSource("br.com.castel.identity.application.MalformedUsernameLoginTest#malformedUsernames")
        void shouldRaiseForMalformedUsernameTheSameExceptionAsForUnknownUser(String malformedUsername) {
            when(repository.findByUsername(anyString())).thenReturn(Optional.empty());
            Throwable unknownUser = catchThrowable(() -> service.login("ghost.user", PASSWORD, CLIENT_IP));

            Throwable malformed = catchThrowable(() -> service.login(malformedUsername, PASSWORD, CLIENT_IP));

            assertThat(malformed).hasSameClassAs(unknownUser).hasMessage(unknownUser.getMessage());
        }
    }

    @Nested
    class WellFormedAfterNormalization {

        @Test
        void shouldFindStoredUserWhenUsernameIsTypedInUppercase() {
            User user = storedUser("joao");

            AuthenticationResult result = service.login("JOAO", PASSWORD, CLIENT_IP);

            assertThat(result.user().id()).isEqualTo(user.id());
        }

        @Test
        void shouldQueryRepositoryWithUsernameAlreadyNormalizedToLowercase() {
            storedUser("joao");

            service.login("JOAO", PASSWORD, CLIENT_IP);

            verify(repository).findByUsername("joao");
        }
    }

    @Nested
    class UnicodeVariant {

        @Test
        void shouldNotAuthenticateUnicodeVariantEvenIfDatabaseWouldMatchItToTheRealUser() {
            User cozinha = storedUser("cozinha");
            when(repository.findByUsername(anyString())).thenReturn(Optional.of(cozinha));

            assertThatThrownBy(() -> service.login(UNICODE_VARIANT_OF_COZINHA, PASSWORD, CLIENT_IP))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        void shouldNotAuthenticateUnicodeVariantAfterTenFailuresOnTheRealUsername() {
            storedUser("cozinha");
            IntStream.range(0, 10).forEach(attempt -> catchThrowable(() -> service.login("cozinha", WRONG_PASSWORD, CLIENT_IP)));

            assertThatThrownBy(() -> service.login(UNICODE_VARIANT_OF_COZINHA, PASSWORD, CLIENT_IP))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        void shouldKeepRealUsernameBlockedAfterUnicodeVariantAttempt() {
            storedUser("cozinha");
            IntStream.range(0, 10).forEach(attempt -> catchThrowable(() -> service.login("cozinha", WRONG_PASSWORD, CLIENT_IP)));
            catchThrowable(() -> service.login(UNICODE_VARIANT_OF_COZINHA, PASSWORD, CLIENT_IP));

            assertThatThrownBy(() -> service.login("cozinha", PASSWORD, CLIENT_IP))
                    .asInstanceOf(type(TooManyLoginAttemptsException.class))
                    .extracting(TooManyLoginAttemptsException::reason)
                    .isEqualTo(TooManyLoginAttemptsException.Reason.FAILURE_LIMIT_REACHED);
        }
    }

    @Nested
    class SharedMalformedUsernamePair {

        @Test
        void shouldBlockAnyMalformedUsernameOfIpAfterTenFailuresOnDistinctMalformedUsernames() {
            failLoginsOnDistinctMalformedUsernames(10);

            assertThatThrownBy(() -> service.login("ab", PASSWORD, CLIENT_IP))
                    .asInstanceOf(type(TooManyLoginAttemptsException.class))
                    .extracting(TooManyLoginAttemptsException::reason)
                    .isEqualTo(TooManyLoginAttemptsException.Reason.FAILURE_LIMIT_REACHED);
        }

        @Test
        void shouldNotBlockMalformedUsernamesAfterNineFailuresOnDistinctMalformedUsernames() {
            failLoginsOnDistinctMalformedUsernames(9);

            assertThatThrownBy(() -> service.login("ab", PASSWORD, CLIENT_IP))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        void shouldKeepWellFormedUsernameOfSameIpFreeAfterTenFailuresOnMalformedUsernames() {
            User user = storedUser("joao.silva");
            failLoginsOnDistinctMalformedUsernames(10);

            AuthenticationResult result = service.login("joao.silva", PASSWORD, CLIENT_IP);

            assertThat(result.user().id()).isEqualTo(user.id());
        }
    }
}
