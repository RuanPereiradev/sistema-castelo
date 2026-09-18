package br.com.castel.app.web;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.app.support.AbstractIntegrationTest;
import br.com.castel.identity.api.Role;
import br.com.castel.identity.domain.User;
import br.com.castel.identity.domain.UserRepository;
import br.com.castel.identity.infra.JwtTokenIssuer;
import br.com.castel.sharedkernel.ConflictException;
import br.com.castel.sharedkernel.DomainException;
import br.com.castel.sharedkernel.NotFoundException;
import jakarta.servlet.Filter;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.io.Serial;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mapping table and invariants of docs/task-0.5b-cross-cutting-foundation.md, items 2, 5 and 6,
 * exercised over a real embedded server so that the security filter chain, the {@code ERROR}
 * dispatch and the problem+json writers are all on the path.
 *
 * <p>The task creates no endpoint, so the situations of the table are provoked by a test-only
 * handler, registered as a {@code @Bean} of a {@link TestConfiguration}. It is nested inside this
 * test class on purpose: Spring Boot's {@code TestTypeExcludeFilter} keeps inner classes of a test
 * out of the component scan of {@code CastelApplication}, which scans all of {@code br.com.castel},
 * so this controller exists only in the context of this class and leaks into no other test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ErrorResponseHttpIntegrationTest.FailingEndpointsConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class ErrorResponseHttpIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Integration-Password-42";
    private static final String OTHER_SECRET = "another-jwt-secret-also-longer-than-256-bits-fedcba9876543210";
    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String BASE = "/api/test-errors";
    private static final String DOMAIN_RULE_CODE = "TEST_DOMAIN_RULE_VIOLATED";
    private static final String CONFLICT_CODE = "TEST_RESOURCE_IN_CONFLICT";
    private static final String NOT_FOUND_CODE = "TEST_RESOURCE_MISSING";
    private static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    private static final String MALFORMED_REQUEST = "MALFORMED_REQUEST";
    private static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    private static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    private static final String UPPER_SNAKE_CASE = "^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$";
    private static final Set<String> RFC_7807_FIELDS = Set.of("type", "title", "status", "detail", "instance");
    private static final Set<String> DESCRIPTIVE_RFC_7807_FIELDS = Set.of("title", "status", "detail", "instance");

    // ------------------------------------------------------------- test-only exceptions

    static class SampleDomainException extends DomainException {

        @Serial
        private static final long serialVersionUID = 1L;

        SampleDomainException() {
            super(DOMAIN_RULE_CODE, "A domain rule of the test endpoint was violated");
        }
    }

    static class SampleConflictException extends ConflictException {

        @Serial
        private static final long serialVersionUID = 1L;

        SampleConflictException() {
            super(CONFLICT_CODE, "The resource of the test endpoint is in conflict");
        }
    }

    static class SampleNotFoundException extends NotFoundException {

        @Serial
        private static final long serialVersionUID = 1L;

        SampleNotFoundException() {
            super(NOT_FOUND_CODE, "The resource of the test endpoint does not exist");
        }
    }

    /** Value the 500 handler must never echo back to the client. */
    static String canary() {
        return "Canary_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public record SampleRequest(@NotBlank String guestName, @Min(1) int quantity) {}

    // ------------------------------------------------------------- test-only endpoints

    @Controller
    @RequestMapping(BASE)
    public static class FailingEndpoints {

        private final Validator validator;

        FailingEndpoints(Validator validator) {
            this.validator = validator;
        }

        @GetMapping("/domain-rule")
        @ResponseBody
        public String domainRule() {
            throw new SampleDomainException();
        }

        @GetMapping("/conflict")
        @ResponseBody
        public String conflict() {
            throw new SampleConflictException();
        }

        @GetMapping("/not-found")
        @ResponseBody
        public String notFound() {
            throw new SampleNotFoundException();
        }

        @GetMapping("/ping")
        @ResponseBody
        public String ping() {
            return "pong";
        }

        @PostMapping("/echo")
        @ResponseBody
        public Map<String, Object> echo(@RequestBody(required = false) byte[] body) {
            return Map.of("receivedBytes", body == null ? 0 : body.length);
        }

        @PostMapping("/validated")
        @ResponseBody
        public String validated(@Valid @RequestBody SampleRequest request) {
            return request.guestName();
        }

        @PostMapping("/manually-validated")
        @ResponseBody
        public String manuallyValidated(@RequestBody SampleRequest request) {
            Set<ConstraintViolation<SampleRequest>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
            return request.guestName();
        }

        @GetMapping("/boom")
        @ResponseBody
        public String boom(@RequestParam("canary") String canary) {
            throw new IllegalStateException("java.sql.SQLException at /opt/castel/Tab.java: " + canary);
        }

        @GetMapping("/admin-only")
        @ResponseBody
        @PreAuthorize("hasRole('ADMIN')")
        public String adminOnly() {
            return "secret";
        }
    }

    // ------------------------------------------------------------- instrumented request body

    /**
     * Bytes the application chain read from the request body of the last request. Registered at the
     * very front of the filter chain, so a body limit that rejects by {@code Content-Length}, or
     * that stops counting at the limit, leaves this far below the size actually sent.
     */
    static final AtomicLong bytesReadFromRequestBody = new AtomicLong();

    static final class CountingRequest extends HttpServletRequestWrapper {

        CountingRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream delegate = super.getInputStream();
            return new ServletInputStream() {

                @Override
                public int read() throws IOException {
                    int value = delegate.read();
                    if (value != -1) {
                        bytesReadFromRequestBody.incrementAndGet();
                    }
                    return value;
                }

                @Override
                public int read(byte[] buffer, int offset, int length) throws IOException {
                    int read = delegate.read(buffer, offset, length);
                    if (read > 0) {
                        bytesReadFromRequestBody.addAndGet(read);
                    }
                    return read;
                }

                @Override
                public boolean isFinished() {
                    return delegate.isFinished();
                }

                @Override
                public boolean isReady() {
                    return delegate.isReady();
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    delegate.setReadListener(readListener);
                }
            };
        }
    }

    @TestConfiguration
    static class FailingEndpointsConfiguration {

        @Bean
        FailingEndpoints failingEndpoints(Validator validator) {
            return new FailingEndpoints(validator);
        }

        @Bean
        FilterRegistrationBean<Filter> requestBodyReadCountingFilter() {
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(
                    (Filter) (request, response, chain) ->
                            chain.doFilter(new CountingRequest((HttpServletRequest) request), response));
            registration.addUrlPatterns("/*");
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }
    }

    // ------------------------------------------------------------- fixtures

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private String waiterToken;

    @BeforeEach
    void createWaiterToken() {
        waiterToken = accessTokenFor(createUser(Role.WAITER));
    }

    private String createUser(Role... roles) {
        String username = "err_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        userRepository.save(User.create(PROPERTY_ID, username, "Error Test User", PASSWORD, Set.of(roles), passwordEncoder));
        return username;
    }

    private String tokenSignedWithAnotherSecret() {
        String username = createUser(Role.WAITER);
        return new JwtTokenIssuer(OTHER_SECRET, clock).issueAccessToken(userRepository.findByUsername(username).orElseThrow());
    }

    private String accessTokenFor(String username) {
        return new JwtTokenIssuer(jwtSecret, clock).issueAccessToken(userRepository.findByUsername(username).orElseThrow());
    }

    // ------------------------------------------------------------- http helpers

    private record HttpResult(int status, String contentType, String body, JsonNode json) {

        String code() {
            return json.path("code").asString();
        }

        String instance() {
            return json.path("instance").asString();
        }
    }

    private HttpResult send(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            return new HttpResult(
                    response.statusCode(),
                    response.headers().firstValue("Content-Type").orElse(""),
                    body,
                    body == null || body.isBlank() ? jsonMapper.createObjectNode() : parseOrEmpty(body));
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

    private HttpResult getAuthenticated(String path) {
        return send(request(path).header("Authorization", "Bearer " + waiterToken).GET());
    }

    private HttpResult getAuthenticatedWith(String path, String token) {
        return send(request(path).header("Authorization", "Bearer " + token).GET());
    }

    private HttpResult getAnonymous(String path) {
        return send(request(path).GET());
    }

    private HttpResult postAuthenticated(String path, String json) {
        return send(request(path)
                .header("Authorization", "Bearer " + waiterToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    private HttpResult methodAuthenticated(String path, HttpMethod method) {
        return send(request(path)
                .header("Authorization", "Bearer " + waiterToken)
                .method(method.name(), HttpRequest.BodyPublishers.noBody()));
    }

    // ------------------------------------------------------------- mapping table (item 2)

    @Nested
    class MappingTable {

        @Test
        void shouldAnswerUnprocessableEntityWithExceptionCodeOnDomainRuleViolation() {
            HttpResult response = getAuthenticated(BASE + "/domain-rule");

            assertThat(response.status()).isEqualTo(422);
            assertThat(response.code()).isEqualTo(DOMAIN_RULE_CODE);
        }

        @Test
        void shouldAnswerConflictWithExceptionCodeOnStateConflict() {
            HttpResult response = getAuthenticated(BASE + "/conflict");

            assertThat(response.status()).isEqualTo(409);
            assertThat(response.code()).isEqualTo(CONFLICT_CODE);
        }

        @Test
        void shouldAnswerNotFoundWithExceptionCodeOnMissingResource() {
            HttpResult response = getAuthenticated(BASE + "/not-found");

            assertThat(response.status()).isEqualTo(404);
            assertThat(response.code()).isEqualTo(NOT_FOUND_CODE);
        }

        @Test
        void shouldAnswerBadRequestWithValidationFailedOnInvalidRequestBody() {
            HttpResult response = postAuthenticated(BASE + "/validated", "{\"guestName\":\"  \",\"quantity\":2}");

            assertThat(response.status()).isEqualTo(400);
            assertThat(response.code()).isEqualTo(VALIDATION_FAILED);
        }

        @Test
        void shouldAnswerBadRequestWithValidationFailedOnConstraintViolationException() {
            HttpResult response = postAuthenticated(BASE + "/manually-validated", "{\"guestName\":\"Ana\",\"quantity\":0}");

            assertThat(response.status()).isEqualTo(400);
            assertThat(response.code()).isEqualTo(VALIDATION_FAILED);
        }

        @Test
        void shouldAnswerBadRequestWithMalformedRequestOnUnreadableBody() {
            HttpResult response = postAuthenticated(BASE + "/validated", "{\"guestName\":");

            assertThat(response.status()).isEqualTo(400);
            assertThat(response.code()).isEqualTo(MALFORMED_REQUEST);
        }

        @Test
        void shouldAnswerNotFoundWithResourceNotFoundOnUnknownRoute() {
            HttpResult response = getAuthenticated("/api/does-not-exist");

            assertThat(response.status()).isEqualTo(404);
            assertThat(response.code()).isEqualTo(RESOURCE_NOT_FOUND);
        }

        @Test
        void shouldAnswerMethodNotAllowedOnUnsupportedMethod() {
            HttpResult response = methodAuthenticated(BASE + "/ping", HttpMethod.DELETE);

            assertThat(response.status()).isEqualTo(405);
            assertThat(response.code()).isEqualTo(METHOD_NOT_ALLOWED);
        }

        @Test
        void shouldAnswerInternalServerErrorWithInternalErrorOnUnexpectedException() {
            HttpResult response = getAuthenticated(BASE + "/boom?canary=" + canary());

            assertThat(response.status()).isEqualTo(500);
            assertThat(response.code()).isEqualTo(INTERNAL_ERROR);
        }
    }

    // ------------------------------------------------------------- RFC 7807 shape

    @Nested
    class ProblemDetailShape {

        private List<HttpResult> everyErrorScenario() {
            List<HttpResult> responses = new ArrayList<>();
            responses.add(getAuthenticated(BASE + "/domain-rule"));
            responses.add(getAuthenticated(BASE + "/conflict"));
            responses.add(getAuthenticated(BASE + "/not-found"));
            responses.add(postAuthenticated(BASE + "/validated", "{\"guestName\":\"  \",\"quantity\":2}"));
            responses.add(postAuthenticated(BASE + "/manually-validated", "{\"guestName\":\"Ana\",\"quantity\":0}"));
            responses.add(postAuthenticated(BASE + "/validated", "{\"guestName\":"));
            responses.add(getAuthenticated("/api/does-not-exist"));
            responses.add(methodAuthenticated(BASE + "/ping", HttpMethod.DELETE));
            responses.add(getAuthenticated(BASE + "/boom?canary=" + canary()));
            responses.add(getAnonymous(BASE + "/ping"));
            return responses;
        }

        @Test
        void shouldNeverAnswerAnErrorWithoutCode() {
            assertThat(everyErrorScenario())
                    .allSatisfy(response -> assertThat(response.code())
                            .as("code of %s", response.body())
                            .isNotBlank());
        }

        @Test
        void shouldWriteEveryErrorCodeInUpperSnakeCase() {
            assertThat(everyErrorScenario())
                    .allSatisfy(response -> assertThat(response.code())
                            .as("code of %s", response.body())
                            .matches(UPPER_SNAKE_CASE));
        }

        @Test
        void shouldWriteEveryErrorInProblemJson() {
            assertThat(everyErrorScenario())
                    .allSatisfy(response -> assertThat(response.contentType())
                            .as("content type of %s", response.body())
                            .contains(PROBLEM_JSON));
        }

        @Test
        void shouldCarryTitleStatusDetailAndInstanceInEveryErrorBody() {
            assertThat(everyErrorScenario()).allSatisfy(response -> assertThat(DESCRIPTIVE_RFC_7807_FIELDS)
                    .allSatisfy(field -> assertThat(response.json().has(field))
                            .as("field %s of %s", field, response.body())
                            .isTrue()));
        }

        /** Item 2 of the spec: "Todo corpo de erro carrega type, title, status, detail, instance e code". */
        @Test
        void shouldCarryTypeInEveryErrorBody() {
            assertThat(everyErrorScenario())
                    .allSatisfy(response -> assertThat(response.json().has("type"))
                            .as("field type of %s", response.body())
                            .isTrue());
        }

        @Test
        void shouldRepeatHttpStatusInsideErrorBody() {
            assertThat(everyErrorScenario())
                    .allSatisfy(response -> assertThat(response.json().path("status").asInt())
                            .as("status of %s", response.body())
                            .isEqualTo(response.status()));
        }

        @Test
        void shouldUseRequestPathAsInstance() {
            HttpResult response = getAuthenticated(BASE + "/conflict");

            assertThat(response.instance()).endsWith(BASE + "/conflict");
        }

        @Test
        void shouldUseRequestPathAsInstanceOnUnknownRoute() {
            HttpResult response = getAuthenticated("/api/does-not-exist");

            assertThat(response.instance()).endsWith("/api/does-not-exist");
        }

        @Test
        void shouldNeverMapSameCodeToTwoDifferentStatuses() {
            Map<String, Integer> statusByCode = new HashMap<>();

            List<HttpResult> responses = everyErrorScenario();

            assertThat(responses).allSatisfy(response -> {
                Integer previous = statusByCode.putIfAbsent(response.code(), response.status());
                assertThat(previous == null ? response.status() : previous)
                        .as("code %s already answered with another status", response.code())
                        .isEqualTo(response.status());
            });
        }
    }

    // ------------------------------------------------------------- validation details

    @Nested
    class ValidationErrors {

        /**
         * The shape assumed here is {@code errors: [{ "field": "...", "code": "..." }]}. The spec
         * (item 2) fixes the content — field name in camelCase and the code of the rule — but not
         * the shape; see the ambiguity list of the task report.
         */
        @Test
        void shouldListInvalidFieldInErrors() {
            HttpResult response = postAuthenticated(BASE + "/validated", "{\"quantity\":2}");

            assertThat(fieldNamesOf(response)).contains("guestName");
        }

        @Test
        void shouldListEveryInvalidFieldInErrors() {
            HttpResult response = postAuthenticated(BASE + "/validated", "{\"guestName\":\"\",\"quantity\":0}");

            assertThat(fieldNamesOf(response)).contains("guestName", "quantity");
        }

        @Test
        void shouldReportRuleCodeInsteadOfDefaultValidatorMessage() {
            HttpResult response = postAuthenticated(BASE + "/validated", "{\"quantity\":2}");

            assertThat(ruleCodesOf(response))
                    .isNotEmpty()
                    .allSatisfy(code -> assertThat(code).matches(UPPER_SNAKE_CASE));
        }

        @Test
        void shouldNotLeakDefaultValidatorMessageInBody() {
            HttpResult response = postAuthenticated(BASE + "/validated", "{\"quantity\":2}");

            assertThat(response.body()).doesNotContain("must not be blank");
        }

        private List<String> fieldNamesOf(HttpResult response) {
            return valuesOf(response, "field");
        }

        private List<String> ruleCodesOf(HttpResult response) {
            return valuesOf(response, "code");
        }

        private List<String> valuesOf(HttpResult response, String property) {
            List<String> values = new ArrayList<>();
            response.json().path("errors").forEach(error -> values.add(error.path(property).asString()));
            return values;
        }
    }

    // ------------------------------------------------------------- 500 does not leak

    @Nested
    class InternalError {

        @Test
        void shouldNotEchoExceptionMessageOnInternalError(CapturedOutput output) {
            String canary = canary();

            HttpResult response = getAuthenticated(BASE + "/boom?canary=" + canary);

            assertThat(response.body()).doesNotContain(canary);
        }

        @Test
        void shouldNotLeakExceptionClassNameOnInternalError() {
            HttpResult response = getAuthenticated(BASE + "/boom?canary=" + canary());

            assertThat(response.body())
                    .doesNotContain("IllegalStateException")
                    .doesNotContain("java.sql")
                    .doesNotContain(".java")
                    .doesNotContain("br.com.castel");
        }

        @Test
        void shouldUseTheSameFixedDetailForEveryInternalError() {
            HttpResult first = getAuthenticated(BASE + "/boom?canary=" + canary());
            HttpResult second = getAuthenticated(BASE + "/boom?canary=" + canary());

            assertThat(first.json().path("detail").asString())
                    .isNotBlank()
                    .isEqualTo(second.json().path("detail").asString());
        }

        @Test
        void shouldRepeatCorrelationIdentifierOfBodyInTheLog(CapturedOutput output) {
            HttpResult response = getAuthenticated(BASE + "/boom?canary=" + canary());

            assertThat(correlationCandidatesOf(response))
                    .as("body %s has no property beyond the RFC 7807 fields to correlate with the log", response.body())
                    .isNotEmpty()
                    .anySatisfy(candidate -> assertThat(output.getAll()).contains(candidate));
        }

        @Test
        void shouldLogStackTraceOfInternalError(CapturedOutput output) {
            String canary = canary();

            getAuthenticated(BASE + "/boom?canary=" + canary);

            assertThat(output.getAll()).contains("IllegalStateException").contains(canary);
        }

        @Test
        void shouldGiveADifferentCorrelationIdentifierToEachInternalError() {
            List<String> first = correlationCandidatesOf(getAuthenticated(BASE + "/boom?canary=" + canary()));
            List<String> second = correlationCandidatesOf(getAuthenticated(BASE + "/boom?canary=" + canary()));

            assertThat(first).isNotEmpty().doesNotContainAnyElementsOf(second);
        }

        /** Every string property of the body that is neither an RFC 7807 field nor the code. */
        private List<String> correlationCandidatesOf(HttpResult response) {
            List<String> candidates = new ArrayList<>();
            response.json().properties().forEach(property -> {
                boolean isRfcField = RFC_7807_FIELDS.contains(property.getKey()) || "code".equals(property.getKey());
                if (!isRfcField && property.getValue().isValueNode()) {
                    String value = property.getValue().asString();
                    if (!value.isBlank()) {
                        candidates.add(value);
                    }
                }
            });
            return candidates;
        }
    }

    // ------------------------------------------------------------- errors written by the security chain

    @Nested
    class SecurityChainErrors {

        @Test
        void shouldCarryInstanceOnUnauthenticatedRequest() {
            HttpResult response = getAnonymous(BASE + "/ping");

            assertThat(response.status()).isEqualTo(401);
            assertThat(response.instance()).endsWith(BASE + "/ping");
        }

        @Test
        void shouldCarryCodeOnUnauthenticatedRequest() {
            HttpResult response = getAnonymous(BASE + "/ping");

            assertThat(response.code()).isNotBlank();
        }

        /** A token with a valid shape and a wrong signature is rejected by the JWT filter itself. */
        @Test
        void shouldCarryInstanceOnErrorWrittenByTheJwtFilter() {
            HttpResult response = getAuthenticatedWith(BASE + "/ping", tokenSignedWithAnotherSecret());

            assertThat(response.status()).isEqualTo(401);
            assertThat(response.code()).isEqualTo("INVALID_TOKEN");
            assertThat(response.instance()).endsWith(BASE + "/ping");
        }

        @Test
        void shouldCarryInstanceOnRequestWithMalformedToken() {
            HttpResult response = getAuthenticatedWith(BASE + "/ping", "not-a-jwt");

            assertThat(response.status()).isEqualTo(401);
            assertThat(response.instance()).endsWith(BASE + "/ping");
        }

        @Test
        void shouldCarryInstanceOnForbiddenRequest() {
            HttpResult response = getAuthenticated(BASE + "/admin-only");

            assertThat(response.status()).isEqualTo(403);
            assertThat(response.instance()).endsWith(BASE + "/admin-only");
        }

        @Test
        void shouldKeepIdentityCodesOnAuthenticationErrors() {
            HttpResult anonymous = getAnonymous(BASE + "/ping");
            HttpResult forbidden = getAuthenticated(BASE + "/admin-only");

            assertThat(anonymous.code()).isEqualTo("AUTHENTICATION_REQUIRED");
            assertThat(forbidden.code()).isEqualTo("ACCESS_DENIED");
        }
    }

    // ------------------------------------------------------------- request body limit (item 2)

    @Nested
    class RequestBodyLimit {

        /** Default of the spec, item 2: 64 KB. */
        private static final int LIMIT_IN_BYTES = 64 * 1024;
        private static final String REQUEST_BODY_TOO_LARGE = "REQUEST_BODY_TOO_LARGE";

        @Test
        void shouldAcceptBodyAtExactlyConfiguredLimit() {
            HttpResult response = postBytes(BASE + "/echo", jsonOfSize(LIMIT_IN_BYTES), waiterToken);

            assertThat(response.status()).isEqualTo(200);
        }

        @Test
        void shouldRejectBodyOneByteAboveConfiguredLimit() {
            HttpResult response = postBytes(BASE + "/echo", jsonOfSize(LIMIT_IN_BYTES + 1), waiterToken);

            assertThat(response.status()).isEqualTo(413);
            assertThat(response.code()).isEqualTo(REQUEST_BODY_TOO_LARGE);
        }

        @Test
        void shouldAnswerRejectedBodyInProblemJsonWithInstance() {
            HttpResult response = postBytes(BASE + "/echo", jsonOfSize(LIMIT_IN_BYTES + 1), waiterToken);

            assertThat(response.contentType()).contains(PROBLEM_JSON);
            assertThat(response.instance()).endsWith(BASE + "/echo");
        }

        @Test
        void shouldNotReachTheEndpointWhenBodyIsTooLarge() {
            HttpResult response = postBytes(BASE + "/echo", jsonOfSize(LIMIT_IN_BYTES + 1), waiterToken);

            assertThat(response.json().has("receivedBytes")).isFalse();
        }

        @Test
        void shouldRejectOversizedBodyBeforeAuthentication() {
            HttpResult response = postBytes(BASE + "/echo", jsonOfSize(LIMIT_IN_BYTES + 1), null);

            assertThat(response.status()).isEqualTo(413);
            assertThat(response.code()).isEqualTo(REQUEST_BODY_TOO_LARGE);
        }

        @Test
        void shouldApplyLimitToPublicRoutesToo() {
            HttpResult response = postBytes("/api/auth/login", jsonOfSize(LIMIT_IN_BYTES + 1), null);

            assertThat(response.status()).isEqualTo(413);
            assertThat(response.code()).isEqualTo(REQUEST_BODY_TOO_LARGE);
        }

        @Test
        void shouldNotLoadOversizedBodyEntirelyWhenRejectingIt() {
            int oversized = 16 * LIMIT_IN_BYTES;
            bytesReadFromRequestBody.set(0);

            HttpResult response = postBytes(BASE + "/echo", jsonOfSize(oversized), waiterToken);

            assertThat(response.status()).isEqualTo(413);
            assertThat(bytesReadFromRequestBody.get())
                    .as("bytes the application chain read out of the %d sent", oversized)
                    .isLessThanOrEqualTo(LIMIT_IN_BYTES + 8192L);
        }

        /**
         * Control for the test above: proves the instrumented stream really sits on the read path,
         * so a count near zero on the oversized request means the body was not read, not that the
         * instrumentation is blind.
         */
        @Test
        void shouldCountBytesOfAnAcceptedBodyThroughTheInstrumentedStream() {
            byte[] accepted = jsonOfSize(4096);
            bytesReadFromRequestBody.set(0);

            HttpResult response = postBytes(BASE + "/echo", accepted, waiterToken);

            assertThat(response.status()).isEqualTo(200);
            assertThat(bytesReadFromRequestBody.get()).isEqualTo(accepted.length);
        }

        /**
         * A chunked request carries no {@code Content-Length}: the limit can only be enforced while the
         * body streams in, otherwise an oversized chunked body walks past it.
         *
         * <p>Two things are asserted: the answer comes before the client finishes sending (so the
         * server does not wait for the end of the body), and the application chain read at most the
         * limit out of the 8 MB announced. The bytes the client managed to write before seeing the
         * answer are not a memory measure: Tomcat swallows part of the rejected body on its own, below
         * the filter chain, to keep the connection usable.
         */
        @Test
        void shouldRejectChunkedBodyAboveLimitWithoutWaitingForTheWholeBody() throws IOException {
            int total = 8 * 1024 * 1024;
            bytesReadFromRequestBody.set(0);

            ChunkedResult result = sendChunkedUntilAnswered(BASE + "/echo", total);

            assertThat(result.status()).isEqualTo(413);
            assertThat(result.bytesSentBeforeAnswer())
                    .as("bytes sent before the answer arrived")
                    .isLessThan(total / 2);
            assertThat(bytesReadFromRequestBody.get())
                    .as("bytes the application chain read out of the %d announced", total)
                    .isLessThanOrEqualTo(LIMIT_IN_BYTES + 8192L);
        }
    }

    // ------------------------------------------------------------- body helpers

    /** Valid JSON of exactly {@code size} bytes, so no parser rejects it before the limit does. */
    private static byte[] jsonOfSize(int size) {
        String prefix = "{\"guestName\":\"";
        String suffix = "\",\"quantity\":1}";
        int padding = size - prefix.length() - suffix.length();
        return (prefix + "A".repeat(Math.max(padding, 0)) + suffix).getBytes(StandardCharsets.UTF_8);
    }

    private HttpResult postBytes(String path, byte[] body, String token) {
        HttpRequest.Builder builder = request(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (token != null) {
            builder = builder.header("Authorization", "Bearer " + token);
        }
        return send(builder);
    }

    private record ChunkedResult(int status, long bytesSentBeforeAnswer) {}

    /**
     * Sends a chunked body over a raw socket, stopping as soon as the server answers, and reports how
     * many body bytes it accepted until then.
     */
    private ChunkedResult sendChunkedUntilAnswered(String path, int totalBytes) throws IOException {
        int chunkSize = 8192;
        byte[] chunk = "A".repeat(chunkSize).getBytes(StandardCharsets.UTF_8);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 5000);
            socket.setSoTimeout(10000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(("POST " + path + " HTTP/1.1\r\n"
                            + "Host: localhost:" + port + "\r\n"
                            + "Authorization: Bearer " + waiterToken + "\r\n"
                            + "Content-Type: text/plain\r\n"
                            + "Transfer-Encoding: chunked\r\n"
                            + "Connection: close\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();

            long sent = 0;
            while (sent < totalBytes && in.available() == 0) {
                try {
                    out.write((Integer.toHexString(chunkSize) + "\r\n").getBytes(StandardCharsets.UTF_8));
                    out.write(chunk);
                    out.write("\r\n".getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    sent += chunkSize;
                } catch (IOException connectionClosedByServer) {
                    break;
                }
            }
            try {
                out.write("0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException connectionClosedByServer) {
                // the server already answered and closed: nothing else to send
            }

            String response = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new ChunkedResult(statusLineOf(response), sent);
        }
    }

    private static int statusLineOf(String rawResponse) {
        String[] parts = rawResponse.split(" ", 3);
        return parts.length < 2 ? -1 : Integer.parseInt(parts[1].trim());
    }

    // ------------------------------------------------------------- happy path control

    @Nested
    class HappyPath {

        @Test
        void shouldAnswerAuthenticatedRequestOnValidRouteWithoutProblemDetail() {
            HttpResult response = getAuthenticated(BASE + "/ping");

            assertThat(response.status()).isEqualTo(200);
            assertThat(response.contentType()).doesNotContain(PROBLEM_JSON);
        }

        @Test
        void shouldAcceptValidRequestBody() {
            HttpResult response = postAuthenticated(BASE + "/validated", "{\"guestName\":\"Ana\",\"quantity\":2}");

            assertThat(response.status()).isEqualTo(200);
        }
    }
}
