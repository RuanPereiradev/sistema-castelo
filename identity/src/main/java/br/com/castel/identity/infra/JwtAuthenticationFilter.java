package br.com.castel.identity.infra;

import br.com.castel.identity.api.AuthenticatedUser;
import br.com.castel.identity.api.Role;
import br.com.castel.identity.application.AuthenticationService;
import br.com.castel.identity.application.InvalidTokenException;
import br.com.castel.identity.application.SessionSupersededException;
import br.com.castel.identity.application.TokenExpiredException;
import br.com.castel.identity.application.UserInactiveException;
import br.com.castel.sharedkernel.DomainException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Authenticates every request that carries a bearer access token, and rejects one that carries an
 * invalid one before it ever reaches a controller.
 *
 * <p>A request with no {@code Authorization} header is let through unauthenticated, and a
 * protected route then falls through to the chain's {@code AuthenticationEntryPoint}. The login
 * and refresh routes are never filtered at all: a stale access token left in the header by the
 * client must not keep the user from signing in again or refreshing.
 *
 * <p>Every decision (token validity, user status, session currency) is taken by
 * {@link AuthenticationService#authenticateAccessToken(String)}; this filter only translates the
 * outcome into a security context or a 401 {@code application/problem+json} body.
 *
 * <p>Deliberately not a Spring-managed bean: {@link JwtSecurityConfigurer} adds it to the security
 * filter chain. Registering it as a bean would make the servlet container run it a second time.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private static final RequestMatcher TOKEN_ISSUING_ROUTES = new OrRequestMatcher(
            PathPatternRequestMatcher.pathPattern("/api/auth/login"),
            PathPatternRequestMatcher.pathPattern("/api/auth/refresh"));

    private final AuthenticationService authenticationService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(AuthenticationService authenticationService, ObjectMapper objectMapper) {
        this.authenticationService = authenticationService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return TOKEN_ISSUING_ROUTES.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = readBearerToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        AuthenticatedUser authenticatedUser;
        try {
            authenticatedUser = authenticationService.authenticateAccessToken(token);
        } catch (TokenExpiredException | InvalidTokenException | UserInactiveException | SessionSupersededException e) {
            writeUnauthorized(request, response, e);
            return;
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                authenticatedUser, null, authoritiesOf(authenticatedUser)));
        SecurityContextHolder.setContext(context);
        filterChain.doFilter(request, response);
    }

    private static String readBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length());
    }

    private static List<GrantedAuthority> authoritiesOf(AuthenticatedUser user) {
        return user.roles().stream()
                .map(Role::springAuthority)
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
    }

    /**
     * Writes the 401 body by hand, because this filter runs before any
     * {@code @RestControllerAdvice}. {@code instance} carries the path of the request, so the shape
     * matches every other error body of the API; only the path, never the query string, which can
     * carry a token.
     */
    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response, DomainException exception)
            throws IOException {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
        problemDetail.setProperty("code", exception.code());
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), problemDetail);
    }
}
