package br.com.castel.identity.infra;

import com.github.benmanes.caffeine.cache.Cache;
import java.time.Clock;
import java.time.Instant;
import java.util.Deque;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Infrastructure beans owned by {@code identity}. */
@Configuration
@EnableConfigurationProperties(LoginRateLimitProperties.class)
public class IdentityInfrastructureConfig {

    private static final int BCRYPT_STRENGTH = 12;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    /** Failed logins per pair (client IP, username), for {@link InMemoryLoginAttemptRateLimiter}. */
    @Bean
    Cache<InMemoryLoginAttemptRateLimiter.IpAndUsername, Deque<Instant>> loginFailuresByPair(
            LoginRateLimitProperties properties, Clock clock) {
        return InMemoryLoginAttemptRateLimiter.newFailuresCache(properties.maxTrackedPairs(), properties.window(), clock);
    }

    /** Failed logins per client IP, for {@link InMemoryLoginAttemptRateLimiter}. */
    @Bean
    Cache<String, Deque<Instant>> loginFailuresByIp(LoginRateLimitProperties properties, Clock clock) {
        return InMemoryLoginAttemptRateLimiter.newFailuresCache(properties.maxTrackedIps(), properties.window(), clock);
    }
}
