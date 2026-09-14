package br.com.castel.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import br.com.castel.identity.api.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Tests written against the {@code User} aggregate specification
 * (docs/task-0.4-identity-auth.md), not against its implementation.
 *
 * <p>{@link BCryptPasswordEncoder} is used as a real, fast-enough encoder. The
 * BCrypt cost factor (12, a production/infra concern) is not this aggregate's
 * responsibility and is not exercised here.
 */
class UserTest {

    private static final UUID PROPERTY_ID = UUID.randomUUID();
    private static final String VALID_USERNAME = "joao.silva";
    private static final String VALID_FULL_NAME = "Joao da Silva";
    private static final String VALID_PASSWORD = "S3cr3t-Value-XYZ-999";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T12:00:00Z"), ZoneOffset.UTC);

    private PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new BCryptPasswordEncoder();
    }

    private User createDefaultUser() {
        return User.create(
                PROPERTY_ID, VALID_USERNAME, VALID_FULL_NAME, VALID_PASSWORD, Set.of(Role.WAITER), encoder);
    }

    @Nested
    class HappyPath {

        @Test
        void shouldCreateUserWithProvidedAttributes() {
            Set<Role> roles = Set.of(Role.ADMIN, Role.WAITER);

            User user = User.create(PROPERTY_ID, VALID_USERNAME, VALID_FULL_NAME, VALID_PASSWORD, roles, encoder);

            assertThat(user.id()).isNotNull();
            assertThat(user.propertyId()).isEqualTo(PROPERTY_ID);
            assertThat(user.username()).isEqualTo(VALID_USERNAME);
            assertThat(user.fullName()).isEqualTo(VALID_FULL_NAME);
            assertThat(user.email()).isNull();
            assertThat(user.roles()).containsExactlyInAnyOrder(Role.ADMIN, Role.WAITER);
        }

        @Test
        void shouldGenerateDifferentIdsForDifferentUsers() {
            User first = createDefaultUser();
            User second = User.create(
                    PROPERTY_ID, "maria.souza", "Maria Souza", VALID_PASSWORD, Set.of(Role.KITCHEN), encoder);

            assertThat(first.id()).isNotEqualTo(second.id());
        }
    }

    @Nested
    class UsernameNormalization {

        @Test
        void shouldNormalizeUsernameToLowercase() {
            User user = User.create(
                    PROPERTY_ID, "Joao.Silva", VALID_FULL_NAME, VALID_PASSWORD, Set.of(Role.WAITER), encoder);

            assertThat(user.username()).isEqualTo("joao.silva");
        }

        @Test
        void shouldRejectNormalizedUsernameContainingInvalidCharacter() {
            // Uppercase alone is not a format violation, but the space still is,
            // even after lowercasing. This pins down the order of operations:
            // normalize first, then validate the format.
            assertThatThrownBy(() -> User.create(
                            PROPERTY_ID,
                            "Joao Silva",
                            VALID_FULL_NAME,
                            VALID_PASSWORD,
                            Set.of(Role.WAITER),
                            encoder))
                    .isInstanceOf(InvalidUsernameException.class);
        }
    }

    // ---------------------------------------------------------------- round 5: format used by login (#62)

    static Stream<String> usernamesAcceptedByCreate() {
        return Stream.of("abc", "a".repeat(30), "joao.silva_2", "user123", "Joao.Silva", "JOAO");
    }

    static Stream<String> usernamesRejectedByCreate() {
        return Stream.of(
                "ab", "a".repeat(31), "joao silva", "Joao Silva", "joao@silva", "", "cozİnha", "a\nb", "joão");
    }

    static Stream<String> usernamesOfBothKinds() {
        return Stream.concat(usernamesAcceptedByCreate(), usernamesRejectedByCreate());
    }

    @Nested
    class UsernameFormatUsedByLogin {

        @ParameterizedTest
        @MethodSource("br.com.castel.identity.domain.UserTest#usernamesAcceptedByCreate")
        void shouldConsiderNormalizedUsernameWellFormedWhenCreateAcceptsIt(String username) {
            assertThat(User.isWellFormedUsername(User.normalizedUsername(username))).isTrue();
        }

        @ParameterizedTest
        @MethodSource("br.com.castel.identity.domain.UserTest#usernamesRejectedByCreate")
        void shouldNotConsiderNormalizedUsernameWellFormedWhenCreateRejectsIt(String username) {
            assertThat(User.isWellFormedUsername(User.normalizedUsername(username))).isFalse();
        }

        @ParameterizedTest
        @MethodSource("br.com.castel.identity.domain.UserTest#usernamesOfBothKinds")
        void shouldAgreeWithCreateOnWhetherUsernameIsAccepted(String username) {
            boolean acceptedByCreate = catchThrowable(() -> User.create(
                            PROPERTY_ID, username, VALID_FULL_NAME, VALID_PASSWORD, Set.of(Role.WAITER), encoder))
                    == null;

            assertThat(User.isWellFormedUsername(User.normalizedUsername(username))).isEqualTo(acceptedByCreate);
        }

        @Test
        void shouldNotConsiderNullUsernameWellFormed() {
            assertThat(User.isWellFormedUsername(null)).isFalse();
        }

        @Test
        void shouldNormalizeUsernameToLowercase() {
            assertThat(User.normalizedUsername("Joao.Silva")).isEqualTo("joao.silva");
        }

        @Test
        void shouldNormalizeUsernameToTheSameValueCreateStores() {
            User user = User.create(
                    PROPERTY_ID, "Joao.Silva", VALID_FULL_NAME, VALID_PASSWORD, Set.of(Role.WAITER), encoder);

            assertThat(User.normalizedUsername("Joao.Silva")).isEqualTo(user.username());
        }

        @Test
        void shouldNormalizeWithRootLocaleRegardlessOfDefaultLocale() {
            Locale previous = Locale.getDefault();
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            try {
                assertThat(User.normalizedUsername("IRIS.LIMA")).isEqualTo("iris.lima");
            } finally {
                Locale.setDefault(previous);
            }
        }

        @Test
        void shouldKeepUnicodeVariantDistinctFromRealUsernameAfterNormalization() {
            assertThat(User.normalizedUsername("cozİnha")).isNotEqualTo("cozinha");
        }
    }

    @Nested
    class UsernameValidation {

        @Test
        void shouldAcceptUsernameWithExactlyThreeCharacters() {
            User user = User.create(
                    PROPERTY_ID, "abc", VALID_FULL_NAME, VALID_PASSWORD, Set.of(Role.WAITER), encoder);

            assertThat(user.username()).isEqualTo("abc");
        }

        @Test
        void shouldRejectUsernameShorterThanThreeCharacters() {
            assertThatThrownBy(() -> User.create(
                            PROPERTY_ID, "ab", VALID_FULL_NAME, VALID_PASSWORD, Set.of(Role.WAITER), encoder))
                    .isInstanceOf(InvalidUsernameException.class);
        }

        @Test
        void shouldAcceptUsernameWithExactlyThirtyCharacters() {
            String username = "a".repeat(30);

            User user = User.create(
                    PROPERTY_ID, username, VALID_FULL_NAME, VALID_PASSWORD, Set.of(Role.WAITER), encoder);

            assertThat(user.username()).isEqualTo(username);
        }

        @Test
        void shouldRejectUsernameLongerThanThirtyCharacters() {
            String username = "a".repeat(31);

            assertThatThrownBy(() -> User.create(
                            PROPERTY_ID, username, VALID_FULL_NAME, VALID_PASSWORD, Set.of(Role.WAITER), encoder))
                    .isInstanceOf(InvalidUsernameException.class);
        }

        @Test
        void shouldAcceptUsernameWithDotAndUnderscore() {
            User user = User.create(
                    PROPERTY_ID, "joao.silva_2", VALID_FULL_NAME, VALID_PASSWORD, Set.of(Role.WAITER), encoder);

            assertThat(user.username()).isEqualTo("joao.silva_2");
        }

        @Test
        void shouldRejectUsernameContainingSpace() {
            assertThatThrownBy(() -> User.create(
                            PROPERTY_ID,
                            "joao silva",
                            VALID_FULL_NAME,
                            VALID_PASSWORD,
                            Set.of(Role.WAITER),
                            encoder))
                    .isInstanceOf(InvalidUsernameException.class);
        }

        @Test
        void shouldRejectUsernameContainingAtSymbol() {
            assertThatThrownBy(() -> User.create(
                            PROPERTY_ID,
                            "joao@silva",
                            VALID_FULL_NAME,
                            VALID_PASSWORD,
                            Set.of(Role.WAITER),
                            encoder))
                    .isInstanceOf(InvalidUsernameException.class);
        }

        @Test
        void shouldRejectEmptyUsername() {
            assertThatThrownBy(() -> User.create(
                            PROPERTY_ID, "", VALID_FULL_NAME, VALID_PASSWORD, Set.of(Role.WAITER), encoder))
                    .isInstanceOf(InvalidUsernameException.class);
        }

        @Test
        void shouldRejectNullUsername() {
            assertThatThrownBy(() -> User.create(
                            PROPERTY_ID, null, VALID_FULL_NAME, VALID_PASSWORD, Set.of(Role.WAITER), encoder))
                    .isInstanceOf(InvalidUsernameException.class);
        }

        @Test
        void shouldExposeInvalidUsernameCode() {
            assertThatThrownBy(() -> User.create(
                            PROPERTY_ID, "ab", VALID_FULL_NAME, VALID_PASSWORD, Set.of(Role.WAITER), encoder))
                    .asInstanceOf(type(InvalidUsernameException.class))
                    .extracting(InvalidUsernameException::code)
                    .isEqualTo("INVALID_USERNAME");
        }
    }

    @Nested
    class PasswordSecurity {

        @Test
        void shouldMatchPasswordUsedAtCreation() {
            User user = createDefaultUser();

            assertThat(user.matches(VALID_PASSWORD, encoder)).isTrue();
        }

        @Test
        void shouldNotMatchWrongPassword() {
            User user = createDefaultUser();

            assertThat(user.matches("wrong-password", encoder)).isFalse();
        }

        @Test
        void shouldMatchNewPasswordAfterChangePassword() {
            User user = createDefaultUser();

            user.changePassword("New-Password-123", encoder);

            assertThat(user.matches("New-Password-123", encoder)).isTrue();
        }

        @Test
        void shouldNotMatchOldPasswordAfterChangePassword() {
            User user = createDefaultUser();

            user.changePassword("New-Password-123", encoder);

            assertThat(user.matches(VALID_PASSWORD, encoder)).isFalse();
        }

        @Test
        void shouldNotExposeRawPasswordInToString() {
            User user = createDefaultUser();

            assertThat(user.toString()).doesNotContain(VALID_PASSWORD);
        }

        @Test
        void shouldNotExposePasswordFragmentsInToString() {
            User user = createDefaultUser();

            assertThat(user.toString()).doesNotContain("S3cr3t").doesNotContain("XYZ-999");
        }

        @Test
        void shouldIncrementTokenVersionOnChangePassword() {
            // Changing the password invalidates sessions issued before it, same as
            // registerLogin()/revokeSessions() — confirmed by the coordinator.
            User user = createDefaultUser();
            int versionBefore = user.tokenVersion();

            user.changePassword("New-Password-123", encoder);

            assertThat(user.tokenVersion()).isEqualTo(versionBefore + 1);
        }
    }

    @Nested
    class SessionManagement {

        @Test
        void shouldIncrementTokenVersionOnRegisterLogin() {
            User user = createDefaultUser();
            int versionBefore = user.tokenVersion();

            user.registerLogin(CLOCK);

            assertThat(user.tokenVersion()).isEqualTo(versionBefore + 1);
        }

        @Test
        void shouldSetLastLoginAtOnRegisterLogin() {
            User user = createDefaultUser();

            user.registerLogin(CLOCK);

            assertThat(user.lastLoginAt()).isEqualTo(CLOCK.instant());
        }

        @Test
        void shouldIncrementTokenVersionOnRevokeSessions() {
            User user = createDefaultUser();
            int versionBefore = user.tokenVersion();

            user.revokeSessions();

            assertThat(user.tokenVersion()).isEqualTo(versionBefore + 1);
        }

        @Test
        void shouldNotChangeLastLoginAtOnRevokeSessions() {
            User user = createDefaultUser();
            user.registerLogin(CLOCK);
            Instant lastLoginAtAfterLogin = user.lastLoginAt();

            user.revokeSessions();

            assertThat(user.lastLoginAt()).isEqualTo(lastLoginAtAfterLogin);
        }

        @Test
        void shouldAccumulateTokenVersionAcrossLoginAndLogoutEvents() {
            User user = createDefaultUser();
            int initialVersion = user.tokenVersion();

            user.registerLogin(CLOCK);
            int afterLogin = user.tokenVersion();
            user.revokeSessions();
            int afterLogout = user.tokenVersion();
            user.registerLogin(CLOCK);
            int afterSecondLogin = user.tokenVersion();

            assertThat(afterLogin).isEqualTo(initialVersion + 1);
            assertThat(afterLogout).isEqualTo(initialVersion + 2);
            assertThat(afterSecondLogin).isEqualTo(initialVersion + 3);
        }

        @Test
        void shouldStartWithTokenVersionZero() {
            // Inferred from the V2 migration (`token_version INTEGER NOT NULL DEFAULT 0`);
            // see AMBIGUIDADES if this does not hold for freshly created, unpersisted aggregates.
            User user = createDefaultUser();

            assertThat(user.tokenVersion()).isZero();
        }

        @Test
        void shouldHaveNullLastLoginAtBeforeFirstLogin() {
            // Not explicitly stated by the spec; see AMBIGUIDADES.
            User user = createDefaultUser();

            assertThat(user.lastLoginAt()).isNull();
        }
    }

    @Nested
    class ActivationState {

        @Test
        void shouldDeactivateActiveUser() {
            User user = createDefaultUser();

            user.deactivate();

            assertThat(user.isActive()).isFalse();
        }

        @Test
        void shouldReactivateDeactivatedUser() {
            User user = createDefaultUser();
            user.deactivate();

            user.activate();

            assertThat(user.isActive()).isTrue();
        }

        @Test
        void shouldIncrementTokenVersionOnDeactivate() {
            // Deactivating a user invalidates sessions issued before it, same as
            // registerLogin()/revokeSessions() — confirmed by the coordinator.
            User user = createDefaultUser();
            int versionBefore = user.tokenVersion();

            user.deactivate();

            assertThat(user.tokenVersion()).isEqualTo(versionBefore + 1);
        }

        @Test
        void shouldBeActiveAfterCreation() {
            // Not explicitly stated by the spec; see AMBIGUIDADES.
            User user = createDefaultUser();

            assertThat(user.isActive()).isTrue();
        }
    }

    @Nested
    class RolesImmutability {

        @Test
        void shouldRejectMutationOfRolesReturnedByAccessor() {
            User user = createDefaultUser();

            assertThatThrownBy(() -> user.roles().add(Role.ADMIN)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void shouldNotBeAffectedByMutatingTheOriginalRolesSetAfterCreation() {
            Set<Role> mutableRoles = new HashSet<>(Set.of(Role.WAITER));

            User user = User.create(
                    PROPERTY_ID, VALID_USERNAME, VALID_FULL_NAME, VALID_PASSWORD, mutableRoles, encoder);
            mutableRoles.add(Role.ADMIN);

            assertThat(user.roles()).containsExactly(Role.WAITER);
        }
    }

    @Nested
    class EqualityById {

        @Test
        void shouldBeEqualToItself() {
            User user = createDefaultUser();

            assertThat(user).isEqualTo(user);
        }

        @Test
        void shouldNotBeEqualToDifferentUser() {
            User first = createDefaultUser();
            User second = User.create(
                    PROPERTY_ID, "maria.souza", "Maria Souza", VALID_PASSWORD, Set.of(Role.KITCHEN), encoder);

            assertThat(first).isNotEqualTo(second);
        }

        @Test
        void shouldNotBeEqualToNull() {
            User user = createDefaultUser();

            assertThat(user).isNotEqualTo(null);
        }

        @Test
        void shouldNotBeEqualToDifferentType() {
            User user = createDefaultUser();

            assertThat(user).isNotEqualTo("not-a-user");
        }
    }

    @Nested
    class AuthenticationEligibility {

        @Test
        void shouldAllowActiveUserToAuthenticate() {
            User user = createDefaultUser();

            assertThat(user.canAuthenticate()).isTrue();
        }

        @Test
        void shouldNotAllowInactiveUserToAuthenticateEvenWithCorrectPassword() {
            User user = createDefaultUser();
            user.deactivate();

            assertThat(user.matches(VALID_PASSWORD, encoder)).isTrue();
            assertThat(user.canAuthenticate()).isFalse();
        }

        @Test
        void shouldAllowReactivatedUserToAuthenticate() {
            User user = createDefaultUser();
            user.deactivate();

            user.activate();

            assertThat(user.canAuthenticate()).isTrue();
        }
    }

    @Nested
    class PasswordStrength {

        private static final String SEVEN_CHARACTERS = "Abc-123";
        private static final String EIGHT_CHARACTERS = "Abc-1234";
        private static final String SEVENTY_TWO_ASCII_BYTES = "Aa1-".repeat(18);
        private static final String SEVENTY_THREE_ASCII_BYTES = SEVENTY_TWO_ASCII_BYTES + "x";
        private static final String SEVENTY_TWO_BYTES_IN_36_CHARACTERS = "ç".repeat(36);
        private static final String SEVENTY_FOUR_BYTES_IN_37_CHARACTERS = "ç".repeat(37);
        private static final String SEVENTY_THREE_BYTES_IN_37_CHARACTERS = "ç".repeat(36) + "a";
        private static final String EMOJI = "😀";
        private static final String SEVEN_EMOJIS = EMOJI.repeat(7);
        private static final String EIGHT_EMOJIS = EMOJI.repeat(8);
        private static final String EIGHTEEN_EMOJIS_IN_72_BYTES = EMOJI.repeat(18);
        private static final String EIGHTEEN_EMOJIS_AND_ONE_ASCII_IN_73_BYTES = EIGHTEEN_EMOJIS_IN_72_BYTES + "a";

        @Test
        void shouldBuildPasswordFixturesWithTheIntendedUtf8Sizes() {
            assertThat(SEVENTY_TWO_ASCII_BYTES.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(72);
            assertThat(SEVENTY_THREE_ASCII_BYTES.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(73);
            assertThat(SEVENTY_TWO_BYTES_IN_36_CHARACTERS.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(72);
            assertThat(SEVENTY_FOUR_BYTES_IN_37_CHARACTERS.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(74);
            assertThat(SEVENTY_FOUR_BYTES_IN_37_CHARACTERS).hasSizeLessThan(72);
        }

        @Test
        void shouldRejectPasswordOneCharacterBelowMinimumOnCreate() {
            assertThatThrownBy(() -> User.create(
                            PROPERTY_ID, VALID_USERNAME, VALID_FULL_NAME, SEVEN_CHARACTERS, Set.of(Role.WAITER), encoder))
                    .asInstanceOf(type(WeakPasswordException.class))
                    .extracting(WeakPasswordException::code)
                    .isEqualTo("WEAK_PASSWORD");
        }

        @Test
        void shouldAcceptPasswordWithExactlyMinimumLengthOnCreate() {
            User user = User.create(
                    PROPERTY_ID, VALID_USERNAME, VALID_FULL_NAME, EIGHT_CHARACTERS, Set.of(Role.WAITER), encoder);

            assertThat(user.matches(EIGHT_CHARACTERS, encoder)).isTrue();
        }

        @Test
        void shouldRejectEmptyPasswordOnCreate() {
            assertThatThrownBy(() -> User.create(
                            PROPERTY_ID, VALID_USERNAME, VALID_FULL_NAME, "", Set.of(Role.WAITER), encoder))
                    .isInstanceOf(WeakPasswordException.class);
        }

        @Test
        void shouldRejectPasswordOneCharacterBelowMinimumOnChangePassword() {
            User user = createDefaultUser();

            assertThatThrownBy(() -> user.changePassword(SEVEN_CHARACTERS, encoder))
                    .asInstanceOf(type(WeakPasswordException.class))
                    .extracting(WeakPasswordException::code)
                    .isEqualTo("WEAK_PASSWORD");
        }

        @Test
        void shouldAcceptPasswordWithExactlyMinimumLengthOnChangePassword() {
            User user = createDefaultUser();

            user.changePassword(EIGHT_CHARACTERS, encoder);

            assertThat(user.matches(EIGHT_CHARACTERS, encoder)).isTrue();
        }

        @Test
        void shouldKeepCurrentPasswordWhenChangePasswordIsRejected() {
            User user = createDefaultUser();

            catchThrowable(() -> user.changePassword(SEVEN_CHARACTERS, encoder));

            assertThat(user.matches(VALID_PASSWORD, encoder)).isTrue();
        }

        @Test
        void shouldKeepTokenVersionWhenChangePasswordIsRejected() {
            User user = createDefaultUser();
            int versionBefore = user.tokenVersion();

            catchThrowable(() -> user.changePassword(SEVEN_CHARACTERS, encoder));

            assertThat(user.tokenVersion()).isEqualTo(versionBefore);
        }

        @Test
        void shouldRejectFourTwoByteCharactersBecauseMinimumCountsCharactersNotBytes() {
            String fourCharactersEightBytes = "çççç";

            assertThatThrownBy(() -> User.create(
                            PROPERTY_ID, VALID_USERNAME, VALID_FULL_NAME, fourCharactersEightBytes, Set.of(Role.WAITER), encoder))
                    .isInstanceOf(WeakPasswordException.class);
        }

        @Test
        void shouldAcceptPasswordWithExactlySeventyTwoBytesOnCreate() {
            User user = User.create(
                    PROPERTY_ID, VALID_USERNAME, VALID_FULL_NAME, SEVENTY_TWO_ASCII_BYTES, Set.of(Role.WAITER), encoder);

            assertThat(user.matches(SEVENTY_TWO_ASCII_BYTES, encoder)).isTrue();
        }

        @Test
        void shouldRejectPasswordWithSeventyThreeAsciiBytesOnCreate() {
            assertThatThrownBy(() -> User.create(
                            PROPERTY_ID, VALID_USERNAME, VALID_FULL_NAME, SEVENTY_THREE_ASCII_BYTES, Set.of(Role.WAITER), encoder))
                    .asInstanceOf(type(PasswordTooLongException.class))
                    .extracting(PasswordTooLongException::code)
                    .isEqualTo("PASSWORD_TOO_LONG");
        }

        @Test
        void shouldAcceptMultibytePasswordWithExactlySeventyTwoBytesOnCreate() {
            User user = User.create(
                    PROPERTY_ID, VALID_USERNAME, VALID_FULL_NAME, SEVENTY_TWO_BYTES_IN_36_CHARACTERS, Set.of(Role.WAITER), encoder);

            assertThat(user.matches(SEVENTY_TWO_BYTES_IN_36_CHARACTERS, encoder)).isTrue();
        }

        @Test
        void shouldRejectMultibytePasswordAboveSeventyTwoBytesWithFewerThanSeventyTwoCharactersOnCreate() {
            assertThatThrownBy(() -> User.create(
                            PROPERTY_ID, VALID_USERNAME, VALID_FULL_NAME, SEVENTY_FOUR_BYTES_IN_37_CHARACTERS, Set.of(Role.WAITER), encoder))
                    .asInstanceOf(type(PasswordTooLongException.class))
                    .extracting(PasswordTooLongException::code)
                    .isEqualTo("PASSWORD_TOO_LONG");
        }

        @Test
        void shouldAcceptPasswordWithExactlySeventyTwoBytesOnChangePassword() {
            User user = createDefaultUser();

            user.changePassword(SEVENTY_TWO_ASCII_BYTES, encoder);

            assertThat(user.matches(SEVENTY_TWO_ASCII_BYTES, encoder)).isTrue();
        }

        @Test
        void shouldRejectPasswordWithSeventyThreeAsciiBytesOnChangePassword() {
            User user = createDefaultUser();

            assertThatThrownBy(() -> user.changePassword(SEVENTY_THREE_ASCII_BYTES, encoder))
                    .asInstanceOf(type(PasswordTooLongException.class))
                    .extracting(PasswordTooLongException::code)
                    .isEqualTo("PASSWORD_TOO_LONG");
        }

        @Test
        void shouldRejectMultibytePasswordAboveSeventyTwoBytesWithFewerThanSeventyTwoCharactersOnChangePassword() {
            User user = createDefaultUser();

            assertThatThrownBy(() -> user.changePassword(SEVENTY_FOUR_BYTES_IN_37_CHARACTERS, encoder))
                    .asInstanceOf(type(PasswordTooLongException.class))
                    .extracting(PasswordTooLongException::code)
                    .isEqualTo("PASSWORD_TOO_LONG");
        }

        @Test
        void shouldKeepCurrentPasswordWhenChangePasswordExceedsSeventyTwoBytes() {
            User user = createDefaultUser();

            catchThrowable(() -> user.changePassword(SEVENTY_THREE_ASCII_BYTES, encoder));

            assertThat(user.matches(VALID_PASSWORD, encoder)).isTrue();
        }

        @Test
        void shouldNotExposeOverlongPasswordInExceptionMessage() {
            Throwable thrown = catchThrowable(() -> User.create(
                    PROPERTY_ID, VALID_USERNAME, VALID_FULL_NAME, SEVENTY_THREE_ASCII_BYTES, Set.of(Role.WAITER), encoder));

            assertThat(thrown).isInstanceOf(PasswordTooLongException.class);
            assertThat(String.valueOf(thrown.getMessage())).doesNotContain(SEVENTY_THREE_ASCII_BYTES);
        }

        @Test
        void shouldBuildEmojiAndMultibyteFixturesWithTheIntendedSizes() {
            assertThat(SEVEN_EMOJIS.codePointCount(0, SEVEN_EMOJIS.length())).isEqualTo(7);
            assertThat(SEVEN_EMOJIS.length()).isEqualTo(14);
            assertThat(EIGHT_EMOJIS.codePointCount(0, EIGHT_EMOJIS.length())).isEqualTo(8);
            assertThat(EIGHTEEN_EMOJIS_IN_72_BYTES.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(72);
            assertThat(EIGHTEEN_EMOJIS_AND_ONE_ASCII_IN_73_BYTES.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .hasSize(73);
            assertThat(SEVENTY_THREE_BYTES_IN_37_CHARACTERS.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(73);
        }

        @Test
        void shouldRejectSevenEmojisOnCreateBecauseMinimumCountsCodePointsNotUtf16Units() {
            assertThatThrownBy(() -> User.create(
                            PROPERTY_ID, VALID_USERNAME, VALID_FULL_NAME, SEVEN_EMOJIS, Set.of(Role.WAITER), encoder))
                    .asInstanceOf(type(WeakPasswordException.class))
                    .extracting(WeakPasswordException::code)
                    .isEqualTo("WEAK_PASSWORD");
        }

        @Test
        void shouldAcceptEightEmojisOnCreate() {
            User user = User.create(
                    PROPERTY_ID, VALID_USERNAME, VALID_FULL_NAME, EIGHT_EMOJIS, Set.of(Role.WAITER), encoder);

            assertThat(user.matches(EIGHT_EMOJIS, encoder)).isTrue();
        }

        @Test
        void shouldRejectSevenEmojisOnChangePassword() {
            User user = createDefaultUser();

            assertThatThrownBy(() -> user.changePassword(SEVEN_EMOJIS, encoder))
                    .asInstanceOf(type(WeakPasswordException.class))
                    .extracting(WeakPasswordException::code)
                    .isEqualTo("WEAK_PASSWORD");
        }

        @Test
        void shouldAcceptEightEmojisOnChangePassword() {
            User user = createDefaultUser();

            user.changePassword(EIGHT_EMOJIS, encoder);

            assertThat(user.matches(EIGHT_EMOJIS, encoder)).isTrue();
        }

        @Test
        void shouldAcceptEmojiPasswordWithExactlySeventyTwoBytesOnCreate() {
            User user = User.create(
                    PROPERTY_ID, VALID_USERNAME, VALID_FULL_NAME, EIGHTEEN_EMOJIS_IN_72_BYTES, Set.of(Role.WAITER), encoder);

            assertThat(user.matches(EIGHTEEN_EMOJIS_IN_72_BYTES, encoder)).isTrue();
        }

        @Test
        void shouldRejectEmojiPasswordWithSeventyThreeBytesOnCreate() {
            assertThatThrownBy(() -> User.create(
                            PROPERTY_ID,
                            VALID_USERNAME,
                            VALID_FULL_NAME,
                            EIGHTEEN_EMOJIS_AND_ONE_ASCII_IN_73_BYTES,
                            Set.of(Role.WAITER),
                            encoder))
                    .asInstanceOf(type(PasswordTooLongException.class))
                    .extracting(PasswordTooLongException::code)
                    .isEqualTo("PASSWORD_TOO_LONG");
        }

        @Test
        void shouldRejectEmojiPasswordWithSeventyThreeBytesOnChangePassword() {
            User user = createDefaultUser();

            assertThatThrownBy(() -> user.changePassword(EIGHTEEN_EMOJIS_AND_ONE_ASCII_IN_73_BYTES, encoder))
                    .asInstanceOf(type(PasswordTooLongException.class))
                    .extracting(PasswordTooLongException::code)
                    .isEqualTo("PASSWORD_TOO_LONG");
        }

        @Test
        void shouldRejectTwoByteCharacterPasswordWithExactlySeventyThreeBytesOnCreate() {
            assertThatThrownBy(() -> User.create(
                            PROPERTY_ID,
                            VALID_USERNAME,
                            VALID_FULL_NAME,
                            SEVENTY_THREE_BYTES_IN_37_CHARACTERS,
                            Set.of(Role.WAITER),
                            encoder))
                    .isInstanceOf(PasswordTooLongException.class);
        }

        @Test
        void shouldRejectTwoByteCharacterPasswordWithExactlySeventyThreeBytesOnChangePassword() {
            User user = createDefaultUser();

            assertThatThrownBy(() -> user.changePassword(SEVENTY_THREE_BYTES_IN_37_CHARACTERS, encoder))
                    .isInstanceOf(PasswordTooLongException.class);
        }

        @Test
        void shouldNotExposeRejectedPasswordInExceptionMessage() {
            Throwable thrown = catchThrowable(() -> User.create(
                    PROPERTY_ID, VALID_USERNAME, VALID_FULL_NAME, SEVEN_CHARACTERS, Set.of(Role.WAITER), encoder));

            assertThat(thrown).isInstanceOf(WeakPasswordException.class);
            assertThat(String.valueOf(thrown.getMessage())).doesNotContain(SEVEN_CHARACTERS);
        }
    }

    @Nested
    class RolesRequirement {

        @Test
        void shouldRejectUserWithoutRoles() {
            assertThatThrownBy(() -> User.create(
                            PROPERTY_ID, VALID_USERNAME, VALID_FULL_NAME, VALID_PASSWORD, Set.of(), encoder))
                    .asInstanceOf(type(UserWithoutRolesException.class))
                    .extracting(UserWithoutRolesException::code)
                    .isEqualTo("USER_WITHOUT_ROLES");
        }

        @Test
        void shouldAcceptUserWithExactlyOneRole() {
            User user = User.create(
                    PROPERTY_ID, VALID_USERNAME, VALID_FULL_NAME, VALID_PASSWORD, Set.of(Role.KITCHEN), encoder);

            assertThat(user.roles()).containsExactly(Role.KITCHEN);
        }
    }

    @Nested
    class SessionCurrency {

        @Test
        void shouldConsiderCurrentTokenVersionAsCurrentSession() {
            User user = createDefaultUser();

            assertThat(user.isSessionCurrent(user.tokenVersion())).isTrue();
        }

        @Test
        void shouldConsiderPreviousTokenVersionSupersededAfterRegisterLogin() {
            User user = createDefaultUser();
            int versionBeforeLogin = user.tokenVersion();

            user.registerLogin(CLOCK);

            assertThat(user.isSessionCurrent(versionBeforeLogin)).isFalse();
        }

        @Test
        void shouldConsiderPreviousTokenVersionSupersededAfterRevokeSessions() {
            User user = createDefaultUser();
            int versionBeforeLogout = user.tokenVersion();

            user.revokeSessions();

            assertThat(user.isSessionCurrent(versionBeforeLogout)).isFalse();
        }

        @Test
        void shouldNotConsiderFutureTokenVersionAsCurrentSession() {
            User user = createDefaultUser();

            assertThat(user.isSessionCurrent(user.tokenVersion() + 1)).isFalse();
        }
    }

    @Nested
    class LoginInstant {

        @Test
        void shouldRecordInstantOfTheClockGivenToTheLatestLogin() {
            User user = createDefaultUser();
            Clock laterClock = Clock.fixed(Instant.parse("2026-09-14T08:30:00Z"), ZoneOffset.UTC);
            user.registerLogin(CLOCK);

            user.registerLogin(laterClock);

            assertThat(user.lastLoginAt()).isEqualTo(Instant.parse("2026-09-14T08:30:00Z"));
        }
    }

    @Nested
    class PasswordStorage {

        @Test
        void shouldStoreEncoderOutputInsteadOfRawPasswordOnCreate() {
            RecordingPasswordEncoder recordingEncoder = new RecordingPasswordEncoder();
            User user = User.create(
                    PROPERTY_ID, VALID_USERNAME, VALID_FULL_NAME, VALID_PASSWORD, Set.of(Role.WAITER), recordingEncoder);

            user.matches(VALID_PASSWORD, recordingEncoder);

            assertThat(recordingEncoder.lastComparedHash).isNotEqualTo(VALID_PASSWORD);
            assertThat(recordingEncoder.lastComparedHash).isEqualTo(recordingEncoder.lastEncodedHash);
        }

        @Test
        void shouldStoreEncoderOutputInsteadOfRawPasswordOnChangePassword() {
            RecordingPasswordEncoder recordingEncoder = new RecordingPasswordEncoder();
            User user = createDefaultUser();
            user.changePassword("New-Password-123", recordingEncoder);

            user.matches("New-Password-123", recordingEncoder);

            assertThat(recordingEncoder.lastComparedHash).isNotEqualTo("New-Password-123");
            assertThat(recordingEncoder.lastComparedHash).isEqualTo(recordingEncoder.lastEncodedHash);
        }

        @Test
        void shouldNotExposePasswordHashInToString() {
            RecordingPasswordEncoder recordingEncoder = new RecordingPasswordEncoder();
            User user = User.create(
                    PROPERTY_ID, VALID_USERNAME, VALID_FULL_NAME, VALID_PASSWORD, Set.of(Role.WAITER), recordingEncoder);

            String description = user.toString();

            assertThat(recordingEncoder.lastEncodedHash).isNotBlank();
            assertThat(description).doesNotContain(recordingEncoder.lastEncodedHash);
        }
    }

    /**
     * Wraps a real BCrypt and records what it produced and what hash it was asked to compare,
     * which is the only way to observe the stored hash without a getter for it.
     */
    private static final class RecordingPasswordEncoder implements PasswordEncoder {

        private final PasswordEncoder delegate = new BCryptPasswordEncoder(4);
        private String lastEncodedHash;
        private String lastComparedHash;

        @Override
        public String encode(CharSequence rawPassword) {
            lastEncodedHash = delegate.encode(rawPassword);
            return lastEncodedHash;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            lastComparedHash = encodedPassword;
            return delegate.matches(rawPassword, encodedPassword);
        }
    }
}
