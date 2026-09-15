package br.com.castel.identity.web;

import br.com.castel.identity.api.AuthenticatedUser;
import br.com.castel.identity.application.AuthenticationResult;
import br.com.castel.identity.application.AuthenticationService;
import br.com.castel.identity.application.RefreshedAccessToken;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Login, refresh, logout and "who am I". */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /**
     * The client IP is {@link HttpServletRequest#getRemoteAddr()}: with
     * {@code server.forward-headers-strategy=native}, Tomcat only replaces it with
     * {@code X-Forwarded-For} when the request came from a trusted proxy.
     */
    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        AuthenticationResult result = authenticationService.login(
                request.getUsername(), request.getPassword(), httpRequest.getRemoteAddr());
        return new LoginResponse(
                result.tokens().accessToken(),
                result.tokens().refreshToken(),
                result.tokens().expiresInSeconds(),
                UserSummaryResponse.from(result.user()));
    }

    @PostMapping("/refresh")
    public RefreshResponse refresh(@RequestBody RefreshRequest request) {
        RefreshedAccessToken refreshed = authenticationService.refresh(request.getRefreshToken());
        return new RefreshResponse(refreshed.accessToken(), refreshed.expiresInSeconds());
    }

    /** Answers 204 with no body. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        authenticationService.logout(currentUser.id());
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return MeResponse.from(authenticationService.me(currentUser.id()));
    }
}
