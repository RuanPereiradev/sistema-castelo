package br.com.castel.app.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import br.com.castel.app.support.AbstractIntegrationTest;
import br.com.castel.identity.api.Role;
import br.com.castel.identity.domain.User;
import br.com.castel.identity.domain.UserRepository;
import br.com.castel.identity.infra.JwtTokenIssuer;
import br.com.castel.identity.infra.LoginRateLimitProperties;
import br.com.castel.sharedkernel.support.MutableClock;
import com.github.benmanes.caffeine.cache.Cache;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Acceptance matrix and error code table of docs/task-0.4-identity-auth.md, exercised over a real
 * embedded server (not MockMvc), so the ERROR dispatch, the security filter chain and the
 * problem+json writers are all on the path. Postgres comes from Testcontainers.
 *
 * <p>Isolation between tests is explicit: before each test the {@link MutableClock} goes back to
 * {@link #BASE_INSTANT} and the login attempt store is emptied. Every clock movement lives inside
 * the test that depends on it, and every test creates its own users, so no test depends on the
 * execution order.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AuthenticationHttpIntegrationTest.ControllableClockConfiguration.class)
class AuthenticationHttpIntegrationTest extends AbstractIntegrationTest {

    private static final Instant BASE_INSTANT = Instant.parse("2026-09-13T12:00:00Z");
    private static final Duration ACCESS_LIFETIME = Duration.ofMinutes(15);
    private static final Duration REFRESH_LIFETIME = Duration.ofDays(7);
    private static final String PASSWORD = "Integration-Password-42";
    private static final String WRONG_PASSWORD = "Wrong-Password-99";
    private static final String OTHER_SECRET = "another-jwt-secret-also-longer-than-256-bits-fedcba9876543210";
    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String BCRYPT_PREFIX = "$2a$";

    @TestConfiguration
    static class ControllableClockConfiguration {

        @Bean
        @Primary
        MutableClock controllableClock() {
            return new MutableClock(BASE_INSTANT);
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private MutableClock clock;

    @Autowired
    @Qualifier("loginFailuresByPair")
    private Cache<?, ?> loginFailuresByPair;

    @Autowired
    @Qualifier("loginFailuresByIp")
    private Cache<?, ?> loginFailuresByIp;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @BeforeEach
    void resetClockAndLoginAttempts() {
        clock.setInstant(BASE_INSTANT);
        loginFailuresByPair.invalidateAll();
        loginFailuresByIp.invalidateAll();
    }

    // ---------------------------------------------------------------- fixtures

    private static String uniqueUsername() {
        return "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /** Only {@code [A-Za-z0-9_]}: a JSON parser reads the whole canary as one unquoted token (#49). */
    private static String canary() {
        return "Canary_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static String loginBodyWithUnquotedPassword(String unquotedPassword) {
        return "{\"username\":\"admin\",\"password\":" + unquotedPassword + "}";
    }

    private static final String PARSER_MESSAGE_MARKER = "Unrecognized token";

    private String createActiveUser(Role... roles) {
        return createActiveUserWithPassword(PASSWORD, roles);
    }

    private String createActiveUserWithPassword(String password, Role... roles) {
        String username = uniqueUsername();
        userRepository.save(User.create(PROPERTY_ID, username, "Integration User", password, Set.of(roles), passwordEncoder));
        return username;
    }

    private String createInactiveUser() {
        return createInactiveUserWithPassword(PASSWORD);
    }

    private String createInactiveUserWithPassword(String password) {
        String username = uniqueUsername();
        User user = User.create(PROPERTY_ID, username, "Inactive User", password, Set.of(Role.WAITER), passwordEncoder);
        user.deactivate();
        userRepository.save(user);
        return username;
    }

    private void deactivate(String username) {
        transactionTemplate.executeWithoutResult(status -> {
            User user = userRepository.findByUsername(username).orElseThrow();
            user.deactivate();
            userRepository.save(user);
        });
    }

    private void removeRoleInDatabase(String username, Role role) {
        jdbcTemplate.update(
                "delete from user_role where role = ? and app_user_id = (select id from app_user where username = ?)",
                role.name(),
                username);
    }

    private void deleteUserInDatabase(String username) {
        jdbcTemplate.update("delete from app_user where username = ?", username);
    }

    private String accessTokenSignedWithOtherSecret(String username) {
        return transactionTemplate.execute(status -> new JwtTokenIssuer(OTHER_SECRET, clock)
                .issueAccessToken(userRepository.findByUsername(username).orElseThrow()));
    }

    private String accessTokenSignedWithTestSecret(String username) {
        return transactionTemplate.execute(status -> new JwtTokenIssuer(jwtSecret, clock)
                .issueAccessToken(userRepository.findByUsername(username).orElseThrow()));
    }

    private String storedPasswordHash(String username) {
        return jdbcTemplate.queryForObject(
                "select password_hash from app_user where username = ?", String.class, username);
    }

    private JsonNode payloadOf(String token) {
        return jsonMapper.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
    }

    // ---------------------------------------------------------------- http

    private record HttpResult(int status, String contentType, String body, JsonNode json) {

        String code() {
            return json.path("code").asString();
        }
    }

    private HttpResult send(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            JsonNode json = body == null || body.isBlank() ? jsonMapper.createObjectNode() : parseOrEmpty(body);
            return new HttpResult(
                    response.statusCode(),
                    response.headers().firstValue("Content-Type").orElse(""),
                    body,
                    json);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private JsonNode parseOrEmpty(String body) {
        try {
            return jsonMapper.readTree(body);
        } catch (RuntimeException notJson) {
            return jsonMapper.createObjectNode();
        }
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private HttpResult postJson(String path, String json) {
        return send(request(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    private HttpResult postJsonWithBearer(String path, String json, String bearer) {
        return send(request(path)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + bearer)
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    private HttpResult postWithoutBodyWithBearer(String path, String bearer) {
        return send(request(path).header("Authorization", "Bearer " + bearer).POST(HttpRequest.BodyPublishers.noBody()));
    }

    private HttpResult get(String path) {
        return send(request(path).GET());
    }

    private HttpResult getWithBearer(String path, String bearer) {
        return send(request(path).header("Authorization", "Bearer " + bearer).GET());
    }

    private HttpResult login(String username, String password) {
        return postJson("/api/auth/login", jsonMapper.writeValueAsString(Map.of("username", username, "password", password)));
    }

    private void failLogins(String username, int count) {
        IntStream.range(0, count).forEach(attempt -> login(username, WRONG_PASSWORD));
    }

    private HttpResult refresh(String refreshToken) {
        return postJson("/api/auth/refresh", jsonMapper.writeValueAsString(Map.of("refreshToken", refreshToken)));
    }

    // ---------------------------------------------------------------- tests

    @Nested
    class Login {

        @Test
        void shouldReturnTokensAndUserRolesOnValidLogin() {
            String username = createActiveUser(Role.WAITER);

            HttpResult response = login(username, PASSWORD);

            assertThat(response.status()).isEqualTo(200);
            assertThat(response.json().path("accessToken").asString()).isNotBlank();
            assertThat(response.json().path("refreshToken").asString()).isNotBlank();
            assertThat(response.json().path("expiresIn").asLong()).isEqualTo(900);
            assertThat(response.json().path("user").path("roles").toString()).contains("WAITER");
        }

        @Test
        void shouldRejectWrongPasswordWithInvalidCredentials() {
            String username = createActiveUser(Role.WAITER);

            HttpResult response = login(username, WRONG_PASSWORD);

            assertThat(response.status()).isEqualTo(401);
            assertThat(response.contentType()).startsWith(PROBLEM_JSON);
            assertThat(response.code()).isEqualTo("INVALID_CREDENTIALS");
        }

        @Test
        void shouldRespondToUnknownUserIdenticallyToWrongPassword() {
            String username = createActiveUser(Role.WAITER);
            HttpResult wrongPassword = login(username, WRONG_PASSWORD);

            HttpResult unknownUser = login("it_nobody_here", WRONG_PASSWORD);

            assertThat(unknownUser.status()).isEqualTo(wrongPassword.status());
            assertThat(unknownUser.contentType()).isEqualTo(wrongPassword.contentType());
            assertThat(unknownUser.body()).isEqualTo(wrongPassword.body());
        }

        @Test
        void shouldRejectEmptyPasswordWithInvalidCredentials() {
            String username = createActiveUser(Role.WAITER);

            HttpResult response = login(username, "");

            assertThat(response.status()).isEqualTo(401);
            assertThat(response.code()).isEqualTo("INVALID_CREDENTIALS");
        }

        @Test
        void shouldRespondToEmptyPasswordOfExistingUserIdenticallyToUnknownUser() {
            String username = createActiveUser(Role.WAITER);
            HttpResult unknownUser = login(uniqueUsername(), WRONG_PASSWORD);

            HttpResult emptyPassword = login(username, "");

            assertThat(emptyPassword.status()).isEqualTo(unknownUser.status());
            assertThat(emptyPassword.contentType()).isEqualTo(unknownUser.contentType());
            assertThat(emptyPassword.body()).isEqualTo(unknownUser.body());
        }

        @Test
        void shouldRejectLoginWithoutPasswordFieldWithInvalidCredentials() {
            String username = createActiveUser(Role.WAITER);

            HttpResult response = postJson("/api/auth/login", "{\"username\":\"" + username + "\"}");

            assertThat(response.status()).isEqualTo(401);
            assertThat(response.code()).isEqualTo("INVALID_CREDENTIALS");
        }

        @Test
        void shouldRespondToLoginWithoutPasswordFieldIdenticallyToUnknownUser() {
            String username = createActiveUser(Role.WAITER);
            HttpResult unknownUser = login(uniqueUsername(), WRONG_PASSWORD);

            HttpResult missingPassword = postJson("/api/auth/login", "{\"username\":\"" + username + "\"}");

            assertThat(missingPassword.status()).isEqualTo(unknownUser.status());
            assertThat(missingPassword.contentType()).isEqualTo(unknownUser.contentType());
            assertThat(missingPassword.body()).isEqualTo(unknownUser.body());
        }

        @Test
        void shouldRespondToInactiveUserIdenticallyToWrongPassword() {
            String activeUsername = createActiveUser(Role.WAITER);
            String inactiveUsername = createInactiveUser();
            HttpResult wrongPassword = login(activeUsername, WRONG_PASSWORD);

            HttpResult inactiveUser = login(inactiveUsername, PASSWORD);

            assertThat(inactiveUser.status()).isEqualTo(wrongPassword.status());
            assertThat(inactiveUser.contentType()).isEqualTo(wrongPassword.contentType());
            assertThat(inactiveUser.body()).isEqualTo(wrongPassword.body());
        }

        @Test
        void shouldRejectInactiveUserWithCorrectPasswordWithInvalidCredentials() {
            String username = createInactiveUser();

            HttpResult response = login(username, PASSWORD);

            assertThat(response.status()).isEqualTo(401);
            assertThat(response.code()).isEqualTo("INVALID_CREDENTIALS");
        }

        @Test
        void shouldAuthenticateSameUserWhenUsernameHasUppercaseLetters() {
            String username = createActiveUser(Role.WAITER);
            String userId = login(username, PASSWORD).json().path("user").path("id").asString();

            HttpResult response = login(username.toUpperCase(), PASSWORD);

            assertThat(response.status()).isEqualTo(200);
            assertThat(response.json().path("user").path("id").asString()).isEqualTo(userId);
        }

        @Test
        void shouldIgnoreInvalidAuthorizationHeaderOnLogin() {
            String username = createActiveUser(Role.WAITER);
            String body = jsonMapper.writeValueAsString(Map.of("username", username, "password", PASSWORD));

            HttpResult response = postJsonWithBearer("/api/auth/login", body, "garbage.token.value");

            assertThat(response.status()).isEqualTo(200);
        }

        @Test
        void shouldNeverPersistRawPassword() {
            String username = createActiveUser(Role.WAITER);

            String storedHash = storedPasswordHash(username);

            assertThat(storedHash).isNotBlank().doesNotContain(PASSWORD);
        }
    }

    @Nested
    class MalformedRequests {

        @Test
        void shouldRejectMalformedJsonOnLoginWithMalformedRequest() {
            HttpResult response = postJson("/api/auth/login", "{\"username\": \"admin\", ");

            assertThat(response.status()).isEqualTo(400);
            assertThat(response.contentType()).startsWith(PROBLEM_JSON);
            assertThat(response.code()).isEqualTo("MALFORMED_REQUEST");
        }

        @Test
        void shouldRejectMissingBodyOnLoginWithMalformedRequest() {
            HttpResult response = send(request("/api/auth/login")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody()));

            assertThat(response.status()).isEqualTo(400);
            assertThat(response.contentType()).startsWith(PROBLEM_JSON);
            assertThat(response.code()).isEqualTo("MALFORMED_REQUEST");
        }

        @Test
        void shouldRejectMalformedJsonOnRefreshWithMalformedRequest() {
            HttpResult response = postJson("/api/auth/refresh", "{\"refreshToken\": ");

            assertThat(response.status()).isEqualTo(400);
            assertThat(response.contentType()).startsWith(PROBLEM_JSON);
            assertThat(response.code()).isEqualTo("MALFORMED_REQUEST");
        }

        @Test
        void shouldNotEchoParserMessageWithUnquotedPasswordInDetail() {
            String canary = canary();

            HttpResult response = postJson("/api/auth/login", loginBodyWithUnquotedPassword(canary));

            assertThat(response.status()).isEqualTo(400);
            assertThat(response.body()).doesNotContain(canary).doesNotContain(PARSER_MESSAGE_MARKER);
        }

        /**
         * Control for the leak guards: the JSON parser on the classpath does echo the whole canary in
         * its error message, so "the canary is absent" proves the message was suppressed, and not
         * that the canary was never going to appear.
         */
        @Test
        void shouldHaveParserMessageThatWouldEchoTheWholeCanary() {
            String canary = canary();

            Throwable parserFailure = catchThrowable(() -> jsonMapper.readTree(loginBodyWithUnquotedPassword(canary)));

            assertThat(parserFailure).isNotNull();
            assertThat(parserFailure.getMessage()).contains(PARSER_MESSAGE_MARKER).contains(canary);
        }
    }

    @Nested
    class LoginRateLimit {

        @Test
        void shouldReturnTooManyRequestsOnEleventhAttemptAfterTenFailures() {
            String username = createActiveUser(Role.WAITER);
            failLogins(username, 10);

            HttpResult response = login(username, WRONG_PASSWORD);

            assertThat(response.status()).isEqualTo(429);
            assertThat(response.contentType()).startsWith(PROBLEM_JSON);
            assertThat(response.code()).isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");
        }

        @Test
        void shouldReturnTooManyRequestsOnEleventhAttemptEvenWithCorrectPassword() {
            String username = createActiveUser(Role.WAITER);
            failLogins(username, 10);

            HttpResult response = login(username, PASSWORD);

            assertThat(response.status()).isEqualTo(429);
            assertThat(response.contentType()).startsWith(PROBLEM_JSON);
            assertThat(response.code()).isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");
        }

        @Test
        void shouldAllowCorrectLoginAfterNineFailures() {
            String username = createActiveUser(Role.WAITER);
            failLogins(username, 9);

            HttpResult response = login(username, PASSWORD);

            assertThat(response.status()).isEqualTo(200);
        }

        @Test
        void shouldBlockNextLoginAfterNineFailuresSeveralSuccessesAndOneFailure() {
            String username = createActiveUser(Role.WAITER);
            failLogins(username, 9);
            IntStream.range(0, 3).forEach(attempt -> login(username, PASSWORD));
            failLogins(username, 1);

            HttpResult response = login(username, PASSWORD);

            assertThat(response.status()).isEqualTo(429);
            assertThat(response.code()).isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");
        }

        @Test
        void shouldKeepBlockingLoginFiftyNineSecondsAfterTenFailures() {
            String username = createActiveUser(Role.WAITER);
            failLogins(username, 10);
            clock.advance(Duration.ofSeconds(59));

            HttpResult response = login(username, PASSWORD);

            assertThat(response.status()).isEqualTo(429);
            assertThat(response.code()).isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");
        }

        @Test
        void shouldAllowLoginSixtyOneSecondsAfterTenFailures() {
            String username = createActiveUser(Role.WAITER);
            failLogins(username, 10);
            clock.advance(Duration.ofSeconds(61));

            HttpResult response = login(username, PASSWORD);

            assertThat(response.status()).isEqualTo(200);
        }

        @Test
        void shouldLetOtherUsernameOfSameIpLogInWhenPairIsBlocked() {
            String adminUsername = createActiveUser(Role.ADMIN);
            String frontDeskUsername = createActiveUser(Role.FRONT_DESK);
            failLogins(adminUsername, 10);

            HttpResult response = login(frontDeskUsername, PASSWORD);

            assertThat(response.status()).isEqualTo(200);
        }

        @Test
        void shouldReturnTooManyRequestsForUnknownUsernameAfterTenFailures() {
            String unknownUsername = uniqueUsername();
            failLogins(unknownUsername, 10);

            HttpResult response = login(unknownUsername, WRONG_PASSWORD);

            assertThat(response.status()).isEqualTo(429);
            assertThat(response.code()).isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");
        }

        @Test
        void shouldReturnTooManyRequestsToPairTypedWithUppercaseAfterTenFailures() {
            String username = createActiveUser(Role.WAITER);
            failLogins(username.toUpperCase(), 10);

            HttpResult response = login(username, PASSWORD);

            assertThat(response.status()).isEqualTo(429);
        }

        @Test
        void shouldReturnTooManyRequestsForNeverUsedUsernameAfterHundredFailuresOnDistinctUsernames() {
            List<Integer> sprayStatuses = sprayFailedLoginsOnDistinctUsernames(100);
            String neverUsedUsername = createActiveUser(Role.WAITER);

            HttpResult response = login(neverUsedUsername, PASSWORD);

            assertThat(sprayStatuses).hasSize(100).containsOnly(401);
            assertThat(response.status()).isEqualTo(429);
            assertThat(response.contentType()).startsWith(PROBLEM_JSON);
            assertThat(response.code()).isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");
        }
    }

    /** Wrong-password logins of distinct unknown usernames, ten at a time, all from the test's IP. */
    private List<Integer> sprayFailedLoginsOnDistinctUsernames(int count) {
        return runConcurrently(IntStream.range(0, count)
                .mapToObj(attempt -> uniqueUsername())
                .<java.util.concurrent.Callable<Integer>>map(username -> () -> login(username, WRONG_PASSWORD).status())
                .toList(), 10);
    }

    private static List<Integer> runConcurrently(List<java.util.concurrent.Callable<Integer>> calls, int threads) {
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = calls.stream()
                    .map(call -> executor.submit(() -> {
                        start.await();
                        return call.call();
                    }))
                    .toList();
            start.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> future : futures) {
                results.add(future.get(2, TimeUnit.MINUTES));
            }
            return results;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * Decision #75: the login capacity is derived from the parameters, never a fixed number.
     * guaranteed = slots x floor(timeout / bcryptDuration), where bcryptDuration is the slowest of
     * several measured samples times a safety margin. Floor: the slots themselves, which never wait.
     * Ceiling: slots + waiting limit, since beyond it the waiting limit rejects before any timeout.
     */
    @Nested
    class ConcurrentPasswordChecks {

        private static final int WARM_UP_MATCHES = 3;
        private static final int MEASURED_MATCHES = 5;
        private static final double SAFETY_MARGIN = 1.5;

        @Autowired
        private LoginRateLimitProperties loginRateLimitProperties;

        private Duration slowestPasswordCheckWithMargin() {
            String hash = passwordEncoder.encode(PASSWORD);
            for (int warmUp = 0; warmUp < WARM_UP_MATCHES; warmUp++) {
                passwordEncoder.matches(PASSWORD, hash);
            }
            long slowestNanos = 0;
            for (int sample = 0; sample < MEASURED_MATCHES; sample++) {
                long started = System.nanoTime();
                passwordEncoder.matches(PASSWORD, hash);
                slowestNanos = Math.max(slowestNanos, System.nanoTime() - started);
            }
            return Duration.ofNanos((long) (slowestNanos * SAFETY_MARGIN));
        }

        private int guaranteedConcurrentLogins(Duration passwordCheck) {
            int slots = loginRateLimitProperties.maxConcurrentPasswordChecksPerIp();
            long checksPerSlotWithinTimeout =
                    loginRateLimitProperties.passwordCheckSlotTimeout().toNanos() / passwordCheck.toNanos();
            long derived = slots * checksPerSlotWithinTimeout;
            long ceiling = (long) slots + loginRateLimitProperties.maxWaitingPasswordChecksPerIp();
            return (int) Math.min(Math.max(derived, slots), ceiling);
        }

        @Test
        void shouldAnswerConcurrentCorrectLoginsWithinSlotCapacityOfSameIpWithOk() {
            Duration passwordCheck = slowestPasswordCheckWithMargin();
            int guaranteed = guaranteedConcurrentLogins(passwordCheck);
            System.out.printf("[#75] passwordCheckWithMargin=%dms guaranteed=%d%n", passwordCheck.toMillis(), guaranteed);
            List<String> usernames =
                    IntStream.range(0, guaranteed).mapToObj(user -> createActiveUser(Role.WAITER)).toList();

            List<Integer> statuses = runConcurrently(usernames.stream()
                    .<java.util.concurrent.Callable<Integer>>map(username -> () -> login(username, PASSWORD).status())
                    .toList(), guaranteed);

            assertThat(statuses)
                    .as("guaranteed=%d, passwordCheckWithMargin=%s", guaranteed, passwordCheck)
                    .hasSize(guaranteed)
                    .containsOnly(200);
        }
    }

    @Nested
    class ProtectedRoutes {

        @Test
        void shouldRequireAuthenticationWhenNoTokenIsSent() {
            HttpResult response = get("/api/auth/me");

            assertThat(response.status()).isEqualTo(401);
            assertThat(response.contentType()).startsWith(PROBLEM_JSON);
            assertThat(response.code()).isEqualTo("AUTHENTICATION_REQUIRED");
        }

        @Test
        void shouldReturnMeWithRolesAndWithoutPasswordHash() {
            String username = createActiveUser(Role.FRONT_DESK);
            String accessToken = login(username, PASSWORD).json().path("accessToken").asString();

            HttpResult response = getWithBearer("/api/auth/me", accessToken);

            assertThat(response.status()).isEqualTo(200);
            assertThat(response.json().path("username").asString()).isEqualTo(username);
            assertThat(response.json().path("roles").toString()).contains("FRONT_DESK");
            assertThat(response.body())
                    .doesNotContain(storedPasswordHash(username))
                    .doesNotContainIgnoringCase("password")
                    .doesNotContain(PASSWORD);
        }

        @Test
        void shouldDenyAdminRouteToWaiterWithAccessDenied() {
            String username = createActiveUser(Role.WAITER);
            String accessToken = login(username, PASSWORD).json().path("accessToken").asString();

            HttpResult response = getWithBearer("/api/admin/users", accessToken);

            assertThat(response.status()).isEqualTo(403);
            assertThat(response.contentType()).startsWith(PROBLEM_JSON);
            assertThat(response.code()).isEqualTo("ACCESS_DENIED");
        }

        @Test
        void shouldListUsersWithoutPasswordHashForAdmin() {
            String admin = createActiveUser(Role.ADMIN);
            String accessToken = login(admin, PASSWORD).json().path("accessToken").asString();

            HttpResult response = getWithBearer("/api/admin/users", accessToken);

            assertThat(response.status()).isEqualTo(200);
            assertThat(response.body())
                    .contains(admin)
                    .doesNotContain(storedPasswordHash(admin))
                    .doesNotContainIgnoringCase("password")
                    .doesNotContain(BCRYPT_PREFIX);
        }

        @Test
        void shouldReturnNotFoundForUnknownRouteWithValidToken() {
            String username = createActiveUser(Role.WAITER);
            String accessToken = login(username, PASSWORD).json().path("accessToken").asString();

            HttpResult response = getWithBearer("/api/route-that-does-not-exist", accessToken);

            assertThat(response.status()).isEqualTo(404);
        }
    }

    @Nested
    class AccessTokens {

        @Test
        void shouldRejectExpiredAccessTokenWithTokenExpired() {
            String username = createActiveUser(Role.WAITER);
            String accessToken = login(username, PASSWORD).json().path("accessToken").asString();
            clock.advance(ACCESS_LIFETIME.plusSeconds(1));

            HttpResult response = getWithBearer("/api/auth/me", accessToken);

            assertThat(response.status()).isEqualTo(401);
            assertThat(response.contentType()).startsWith(PROBLEM_JSON);
            assertThat(response.code()).isEqualTo("TOKEN_EXPIRED");
        }

        @Test
        void shouldAcceptAccessTokenAtTheExactExpirationSecond() {
            String username = createActiveUser(Role.WAITER);
            String accessToken = login(username, PASSWORD).json().path("accessToken").asString();
            clock.setInstant(BASE_INSTANT.plus(ACCESS_LIFETIME));

            HttpResult response = getWithBearer("/api/auth/me", accessToken);

            assertThat(response.status()).isEqualTo(200);
        }

        @Test
        void shouldAcceptAccessTokenOneSecondBeforeExpiration() {
            String username = createActiveUser(Role.WAITER);
            String accessToken = login(username, PASSWORD).json().path("accessToken").asString();
            clock.setInstant(BASE_INSTANT.plus(ACCESS_LIFETIME).minusSeconds(1));

            HttpResult response = getWithBearer("/api/auth/me", accessToken);

            assertThat(response.status()).isEqualTo(200);
        }

        @Test
        void shouldRejectMalformedTokenWithInvalidToken() {
            HttpResult response = getWithBearer("/api/auth/me", "this-is-not-a-jwt");

            assertThat(response.status()).isEqualTo(401);
            assertThat(response.contentType()).startsWith(PROBLEM_JSON);
            assertThat(response.code()).isEqualTo("INVALID_TOKEN");
        }

        @Test
        void shouldRejectTokenSignedWithAnotherSecretWithInvalidToken() {
            String username = createActiveUser(Role.WAITER);
            login(username, PASSWORD);
            String foreignToken = accessTokenSignedWithOtherSecret(username);

            HttpResult response = getWithBearer("/api/auth/me", foreignToken);

            assertThat(response.status()).isEqualTo(401);
            assertThat(response.code()).isEqualTo("INVALID_TOKEN");
        }

        @Test
        void shouldAcceptTokenGeneratedInTestWithTestSecretAndCurrentVersion() {
            String username = createActiveUser(Role.WAITER);
            login(username, PASSWORD);
            String token = accessTokenSignedWithTestSecret(username);

            HttpResult response = getWithBearer("/api/auth/me", token);

            assertThat(response.status()).isEqualTo(200);
        }

        @Test
        void shouldRejectRefreshTokenUsedAsAccessTokenWithInvalidToken() {
            String username = createActiveUser(Role.WAITER);
            String refreshToken = login(username, PASSWORD).json().path("refreshToken").asString();

            HttpResult response = getWithBearer("/api/auth/me", refreshToken);

            assertThat(response.status()).isEqualTo(401);
            assertThat(response.code()).isEqualTo("INVALID_TOKEN");
        }
    }

    @Nested
    class Refresh {

        @Test
        void shouldIssueUsableAccessTokenOnValidRefresh() {
            String username = createActiveUser(Role.WAITER);
            String refreshToken = login(username, PASSWORD).json().path("refreshToken").asString();

            HttpResult response = refresh(refreshToken);

            assertThat(response.status()).isEqualTo(200);
            String newAccessToken = response.json().path("accessToken").asString();
            assertThat(getWithBearer("/api/auth/me", newAccessToken).status()).isEqualTo(200);
        }

        @Test
        void shouldNotCarryRolesInRefreshTokenIssuedAtLogin() {
            String username = createActiveUser(Role.WAITER);
            String refreshToken = login(username, PASSWORD).json().path("refreshToken").asString();

            JsonNode payload = payloadOf(refreshToken);

            assertThat(payload.has("roles")).isFalse();
        }

        @Test
        void shouldReloadRolesFromDatabaseWhenRefreshingAccessToken() {
            String username = createActiveUser(Role.WAITER, Role.FRONT_DESK);
            String refreshToken = login(username, PASSWORD).json().path("refreshToken").asString();
            removeRoleInDatabase(username, Role.FRONT_DESK);

            HttpResult response = refresh(refreshToken);

            assertThat(response.status()).isEqualTo(200);
            JsonNode roles = payloadOf(response.json().path("accessToken").asString()).path("roles");
            assertThat(roles.toString()).contains("WAITER").doesNotContain("FRONT_DESK");
        }

        @Test
        void shouldRejectAccessTokenUsedAsRefreshTokenWithInvalidToken() {
            String username = createActiveUser(Role.WAITER);
            String accessToken = login(username, PASSWORD).json().path("accessToken").asString();

            HttpResult response = refresh(accessToken);

            assertThat(response.status()).isEqualTo(401);
            assertThat(response.contentType()).startsWith(PROBLEM_JSON);
            assertThat(response.code()).isEqualTo("INVALID_TOKEN");
        }

        @Test
        void shouldRejectMalformedRefreshTokenWithInvalidToken() {
            HttpResult response = refresh("this-is-not-a-jwt");

            assertThat(response.status()).isEqualTo(401);
            assertThat(response.code()).isEqualTo("INVALID_TOKEN");
        }

        @Test
        void shouldAcceptRefreshTokenAtTheExactExpirationSecond() {
            String username = createActiveUser(Role.WAITER);
            String refreshToken = login(username, PASSWORD).json().path("refreshToken").asString();
            clock.setInstant(BASE_INSTANT.plus(REFRESH_LIFETIME));

            HttpResult response = refresh(refreshToken);

            assertThat(response.status()).isEqualTo(200);
        }

        @Test
        void shouldRefreshWhenExpiredAccessTokenIsSentInAuthorizationHeader() {
            String username = createActiveUser(Role.WAITER);
            JsonNode tokens = login(username, PASSWORD).json();
            String expiredAccessToken = tokens.path("accessToken").asString();
            String body = jsonMapper.writeValueAsString(Map.of("refreshToken", tokens.path("refreshToken").asString()));
            clock.advance(Duration.ofMinutes(16));

            HttpResult response = postJsonWithBearer("/api/auth/refresh", body, expiredAccessToken);

            assertThat(response.status()).isEqualTo(200);
        }

        @Test
        void shouldRejectRefreshOfDeactivatedUserWithUserInactive() {
            String username = createActiveUser(Role.WAITER);
            String refreshToken = login(username, PASSWORD).json().path("refreshToken").asString();
            deactivate(username);

            HttpResult response = refresh(refreshToken);

            assertThat(response.status()).isEqualTo(401);
            assertThat(response.contentType()).startsWith(PROBLEM_JSON);
            assertThat(response.code()).isEqualTo("USER_INACTIVE");
        }

        @Test
        void shouldRejectRefreshOfDeletedUserWithSessionSuperseded() {
            String username = createActiveUser(Role.WAITER);
            String refreshToken = login(username, PASSWORD).json().path("refreshToken").asString();
            deleteUserInDatabase(username);

            HttpResult response = refresh(refreshToken);

            assertThat(response.status()).isEqualTo(401);
            assertThat(response.code()).isEqualTo("SESSION_SUPERSEDED");
        }
    }

    @Nested
    class SingleSession {

        @Test
        void shouldRejectAccessTokenAfterLoginOnAnotherDeviceWithSessionSuperseded() {
            String username = createActiveUser(Role.WAITER);
            String firstAccessToken = login(username, PASSWORD).json().path("accessToken").asString();
            login(username, PASSWORD);

            HttpResult response = getWithBearer("/api/auth/me", firstAccessToken);

            assertThat(response.status()).isEqualTo(401);
            assertThat(response.contentType()).startsWith(PROBLEM_JSON);
            assertThat(response.code()).isEqualTo("SESSION_SUPERSEDED");
        }

        @Test
        void shouldKeepLatestAccessTokenValidAfterLoginOnAnotherDevice() {
            String username = createActiveUser(Role.WAITER);
            login(username, PASSWORD);
            String latestAccessToken = login(username, PASSWORD).json().path("accessToken").asString();

            HttpResult response = getWithBearer("/api/auth/me", latestAccessToken);

            assertThat(response.status()).isEqualTo(200);
        }

        @Test
        void shouldReturnNoContentWithoutBodyOnLogout() {
            String username = createActiveUser(Role.WAITER);
            String accessToken = login(username, PASSWORD).json().path("accessToken").asString();

            HttpResult logout = postWithoutBodyWithBearer("/api/auth/logout", accessToken);

            assertThat(logout.status()).isEqualTo(204);
            assertThat(logout.body()).isEmpty();
        }

        @Test
        void shouldRejectAccessTokenAfterLogoutWithSessionSuperseded() {
            String username = createActiveUser(Role.WAITER);
            String accessToken = login(username, PASSWORD).json().path("accessToken").asString();
            postWithoutBodyWithBearer("/api/auth/logout", accessToken);

            HttpResult response = getWithBearer("/api/auth/me", accessToken);

            assertThat(response.status()).isEqualTo(401);
            assertThat(response.code()).isEqualTo("SESSION_SUPERSEDED");
        }

        @Test
        void shouldRequireAuthenticationForLogout() {
            HttpResult response = send(request("/api/auth/logout").POST(HttpRequest.BodyPublishers.noBody()));

            assertThat(response.status()).isEqualTo(401);
            assertThat(response.code()).isEqualTo("AUTHENTICATION_REQUIRED");
        }

        @Test
        void shouldRejectAccessTokenOfDeactivatedUserWithUserInactive() {
            String username = createActiveUser(Role.WAITER);
            String accessToken = login(username, PASSWORD).json().path("accessToken").asString();
            deactivate(username);

            HttpResult response = getWithBearer("/api/auth/me", accessToken);

            assertThat(response.status()).isEqualTo(401);
            assertThat(response.contentType()).startsWith(PROBLEM_JSON);
            assertThat(response.code()).isEqualTo("USER_INACTIVE");
        }

        @Test
        void shouldRejectAccessTokenOfDeletedUserWithSessionSuperseded() {
            String username = createActiveUser(Role.WAITER);
            String accessToken = login(username, PASSWORD).json().path("accessToken").asString();
            deleteUserInDatabase(username);

            HttpResult response = getWithBearer("/api/auth/me", accessToken);

            assertThat(response.status()).isEqualTo(401);
            assertThat(response.code()).isEqualTo("SESSION_SUPERSEDED");
        }
    }

    /**
     * Regression guard: no password, and no BCrypt hash, reaches the application output. Each
     * test uses a unique canary so a leak cannot come from another test.
     */
    @Nested
    @ExtendWith(OutputCaptureExtension.class)
    class PasswordNeverReachesLog {

        private static final String FAILED_LOGIN_AUDIT = "login failed";

        @Test
        void shouldCaptureTheFailedLoginAuditLogOfTheApplication(CapturedOutput output) {
            String username = createActiveUser(Role.WAITER);

            login(username, WRONG_PASSWORD);

            assertThat(output.getAll()).contains(FAILED_LOGIN_AUDIT);
        }

        @Test
        void shouldNotLogUnquotedPasswordOfMalformedBody(CapturedOutput output) {
            String canary = canary();

            HttpResult response = postJson("/api/auth/login", loginBodyWithUnquotedPassword(canary));

            assertThat(response.status()).isEqualTo(400);
            assertThat(response.contentType()).startsWith(PROBLEM_JSON);
            assertThat(response.code()).isEqualTo("MALFORMED_REQUEST");
            assertThat(response.body()).doesNotContain(canary).doesNotContain(PARSER_MESSAGE_MARKER);
            assertThat(output.getAll())
                    .doesNotContain(canary)
                    .doesNotContain(PARSER_MESSAGE_MARKER)
                    .doesNotContain(BCRYPT_PREFIX);
        }

        @Test
        void shouldNotLogWrongPasswordOfExistingUser(CapturedOutput output) {
            String username = createActiveUser(Role.WAITER);
            String canary = canary();

            HttpResult response = login(username, canary);

            assertThat(response.status()).isEqualTo(401);
            assertThat(output.getAll()).contains(FAILED_LOGIN_AUDIT).doesNotContain(canary).doesNotContain(BCRYPT_PREFIX);
        }

        @Test
        void shouldNotLogPasswordOfUnknownUser(CapturedOutput output) {
            String canary = canary();

            HttpResult response = login(uniqueUsername(), canary);

            assertThat(response.status()).isEqualTo(401);
            assertThat(output.getAll()).contains(FAILED_LOGIN_AUDIT).doesNotContain(canary).doesNotContain(BCRYPT_PREFIX);
        }

        @Test
        void shouldNotLogCorrectPasswordOfInactiveUser(CapturedOutput output) {
            String canary = canary();
            String username = createInactiveUserWithPassword(canary);

            HttpResult response = login(username, canary);

            assertThat(response.status()).isEqualTo(401);
            assertThat(output.getAll()).contains(FAILED_LOGIN_AUDIT).doesNotContain(canary).doesNotContain(BCRYPT_PREFIX);
        }

        @Test
        void shouldNotLogPasswordNorStoredHashOnSuccessfulLogin(CapturedOutput output) {
            String canary = canary();
            String username = createActiveUserWithPassword(canary, Role.WAITER);

            HttpResult response = login(username, canary);

            String storedHash = storedPasswordHash(username);
            assertThat(response.status()).isEqualTo(200);
            assertThat(output.getAll()).doesNotContain(canary).doesNotContain(storedHash).doesNotContain(BCRYPT_PREFIX);
        }
    }

    // ---------------------------------------------------------------- round 5

    private static final String LOOPBACK_IP = "127.0.0.1";

    /** Same as {@link #login}, but through the IPv4 loopback, so the server sees {@value #LOOPBACK_IP}. */
    private HttpResult loginFromLoopbackIpv4(String username, String password) {
        return send(HttpRequest.newBuilder(URI.create("http://" + LOOPBACK_IP + ":" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        jsonMapper.writeValueAsString(Map.of("username", username, "password", password)))));
    }

    /** {@code it_...} becomes {@code İt_...}: lowercases to {@code i̇t_...}, never to the real username. */
    private static String unicodeVariantOf(String username) {
        return username.replaceFirst("i", "İ");
    }

    /** Decision #62 over HTTP. */
    @Nested
    class MalformedUsername {

        @Test
        void shouldRejectUnicodeVariantOfExistingUsernameEvenWithCorrectPassword() {
            String username = createActiveUser(Role.KITCHEN);

            HttpResult response = login(unicodeVariantOf(username), PASSWORD);

            assertThat(response.status()).isEqualTo(401);
            assertThat(response.code()).isEqualTo("INVALID_CREDENTIALS");
        }

        @Test
        void shouldNotAuthenticateUnicodeVariantAfterTenFailuresOnTheRealUsername() {
            String username = createActiveUser(Role.KITCHEN);
            failLogins(username, 10);

            HttpResult response = login(unicodeVariantOf(username), PASSWORD);

            assertThat(response.status()).isEqualTo(401);
            assertThat(response.code()).isEqualTo("INVALID_CREDENTIALS");
        }

        @Test
        void shouldKeepRealUsernameBlockedAfterUnicodeVariantLoginWithCorrectPassword() {
            String username = createActiveUser(Role.KITCHEN);
            failLogins(username, 10);
            login(unicodeVariantOf(username), PASSWORD);

            HttpResult response = login(username, PASSWORD);

            assertThat(response.status()).isEqualTo(429);
            assertThat(response.code()).isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");
        }

        @Test
        void shouldRespondToUnicodeVariantIdenticallyToUnknownUser() {
            String username = createActiveUser(Role.KITCHEN);
            HttpResult unknownUser = login(uniqueUsername(), PASSWORD);

            HttpResult unicodeVariant = login(unicodeVariantOf(username), PASSWORD);

            assertThat(unicodeVariant.status()).isEqualTo(unknownUser.status());
            assertThat(unicodeVariant.contentType()).isEqualTo(unknownUser.contentType());
            assertThat(unicodeVariant.body()).isEqualTo(unknownUser.body());
        }
    }

    /** Decision #61 over HTTP: no queue on the row of an existing user. */
    @Nested
    class ConcurrentFailuresTiming {

        private Duration timeThreeConcurrentWrongPasswordLogins(String username) {
            return timeThreeConcurrentFailedLogins(username, WRONG_PASSWORD);
        }

        private Duration timeThreeConcurrentFailedLogins(String username, String password) {
            long started = System.nanoTime();
            List<Integer> statuses = runConcurrently(IntStream.range(0, 3)
                    .<java.util.concurrent.Callable<Integer>>mapToObj(attempt -> () -> login(username, password).status())
                    .toList(), 3);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
            assertThat(statuses).as("every timed login should be a credential failure").containsOnly(401);
            return elapsed;
        }

        @Test
        void shouldCompleteConcurrentWrongPasswordsOfExistingUserWithinTwiceTheTimeOfUnknownUser() {
            String existingUsername = createActiveUser(Role.WAITER);
            String unknownUsername = uniqueUsername();
            login(existingUsername, WRONG_PASSWORD);
            login(unknownUsername, WRONG_PASSWORD);

            Duration unknownFirst = timeThreeConcurrentWrongPasswordLogins(unknownUsername);
            Duration existingFirst = timeThreeConcurrentWrongPasswordLogins(existingUsername);
            Duration existingSecond = timeThreeConcurrentWrongPasswordLogins(existingUsername);
            Duration unknownSecond = timeThreeConcurrentWrongPasswordLogins(unknownUsername);

            assertThat(existingFirst.plus(existingSecond))
                    .isLessThanOrEqualTo(unknownFirst.plus(unknownSecond).multipliedBy(2));
        }

        @Test
        void shouldCompleteConcurrentEmptyPasswordsOfExistingUserWithinTwiceTheTimeOfUnknownUser() {
            String existingUsername = createActiveUser(Role.WAITER);
            String unknownUsername = uniqueUsername();
            login(existingUsername, "");
            login(unknownUsername, "");

            Duration unknownFirst = timeThreeConcurrentFailedLogins(unknownUsername, "");
            Duration existingFirst = timeThreeConcurrentFailedLogins(existingUsername, "");
            Duration existingSecond = timeThreeConcurrentFailedLogins(existingUsername, "");
            Duration unknownSecond = timeThreeConcurrentFailedLogins(unknownUsername, "");

            assertThat(existingFirst.plus(existingSecond))
                    .isLessThanOrEqualTo(unknownFirst.plus(unknownSecond).multipliedBy(2));
        }
    }

    /**
     * Connection pool during a burst of failures. The test profile does not trust
     * {@code X-Forwarded-For}, so every request comes from the same IP and at most 3 password
     * checks run at once; the many-IP version lives in LoginWithoutRowLockTest (identity).
     */
    @Nested
    class PoolDuringFailureBurst {

        @Test
        void shouldAnswerMeOfAnotherUserWhileBurstOfFailedLoginsIsInFlight() throws Exception {
            String bystander = createActiveUser(Role.FRONT_DESK);
            String accessToken = login(bystander, PASSWORD).json().path("accessToken").asString();
            List<String> targets = IntStream.range(0, 6).mapToObj(user -> createActiveUser(Role.WAITER)).toList();

            try (ExecutorService executor = Executors.newFixedThreadPool(targets.size())) {
                List<Future<Integer>> burst = targets.stream()
                        .map(username -> executor.submit(() -> login(username, WRONG_PASSWORD).status()))
                        .toList();
                HttpResult me = getWithBearer("/api/auth/me", accessToken);
                boolean burstStillInFlight = burst.stream().anyMatch(attempt -> !attempt.isDone());
                for (Future<Integer> attempt : burst) {
                    attempt.get(1, TimeUnit.MINUTES);
                }

                assertThat(burstStillInFlight).as("the burst should still be running when /me answered").isTrue();
                assertThat(me.status()).isEqualTo(200);
            }
        }
    }

    /** Decisions #64 and #68: defaults bound from application.yml. */
    @Nested
    class LoginRateLimitDefaults {

        @Autowired
        private LoginRateLimitProperties loginRateLimitProperties;

        @Test
        void shouldWaitAtMostTwoSecondsForPasswordCheckSlotByDefault() {
            assertThat(loginRateLimitProperties.passwordCheckSlotTimeout()).isEqualTo(Duration.ofSeconds(2));
        }

        @Test
        void shouldAllowAtMostThirtyWaitingPasswordChecksPerIpByDefault() {
            assertThat(loginRateLimitProperties.maxWaitingPasswordChecksPerIp()).isEqualTo(30);
        }

        @Test
        void shouldAllowAtMostThreeConcurrentPasswordChecksPerIpByDefault() {
            assertThat(loginRateLimitProperties.maxConcurrentPasswordChecksPerIp()).isEqualTo(3);
        }
    }

    /** Decision #69 over HTTP. */
    @Nested
    @ExtendWith(OutputCaptureExtension.class)
    class AuditLog {

        private static final String JWT_PREFIX = "eyJ";

        @Test
        void shouldLogQuotedUsernameOfUnknownWellFormedUser(CapturedOutput output) {
            String username = uniqueUsername();

            login(username, WRONG_PASSWORD);

            assertThat(output.getAll()).contains("\"" + username + "\"");
        }

        @Test
        void shouldNotStartAnyLogLineWithTextInjectedThroughLineFeedInUsername(CapturedOutput output) {
            String injected = "FORGED_" + UUID.randomUUID().toString().replace("-", "");

            HttpResult response = login("it_probe\n" + injected, WRONG_PASSWORD);

            assertThat(response.status()).isEqualTo(401);
            assertThat(output.getAll().lines()).noneMatch(line -> line.startsWith(injected));
        }

        @Test
        void shouldKeepTextInjectedThroughLineFeedOnTheLogLineOfTheAttempt(CapturedOutput output) {
            String prefix = uniqueUsername();
            String injected = "FORGED_" + UUID.randomUUID().toString().replace("-", "");

            login(prefix + "\n" + injected, WRONG_PASSWORD);

            assertThat(output.getAll().lines()).anyMatch(line -> line.contains(prefix) && line.contains(injected));
        }

        @Test
        void shouldLogEveryFailureAndTheTooManyAttemptsRejectionWithIpAndUsername(CapturedOutput output) {
            String username = createActiveUser(Role.WAITER);
            String quotedUsername = "\"" + username + "\"";
            IntStream.range(0, 10).forEach(attempt -> loginFromLoopbackIpv4(username, WRONG_PASSWORD));

            HttpResult rejected = loginFromLoopbackIpv4(username, PASSWORD);

            assertThat(rejected.status()).isEqualTo(429);
            assertThat(output.getAll().lines().filter(line -> line.contains(quotedUsername) && line.contains(LOOPBACK_IP)))
                    .hasSizeGreaterThanOrEqualTo(11);
        }

        @Test
        void shouldNotLogTokenNorPasswordOnSuccessfulLogin(CapturedOutput output) {
            String canary = canary();
            String username = createActiveUserWithPassword(canary, Role.WAITER);

            HttpResult response = login(username, canary);

            assertThat(response.status()).isEqualTo(200);
            assertThat(output.getAll()).doesNotContain(JWT_PREFIX).doesNotContain(canary);
        }

        @Test
        void shouldNotLogTokenPasswordNorHashOnTooManyAttemptsRejection(CapturedOutput output) {
            String canary = canary();
            String username = createActiveUserWithPassword(canary, Role.WAITER);
            failLogins(username, 10);

            HttpResult response = login(username, canary);

            assertThat(response.status()).isEqualTo(429);
            assertThat(output.getAll()).doesNotContain(JWT_PREFIX).doesNotContain(canary).doesNotContain(BCRYPT_PREFIX);
        }
    }
}
