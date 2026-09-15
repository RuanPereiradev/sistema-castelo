package br.com.castel.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

/**
 * "TRUSTED_PROXIES vazio, em branco ou com placeholder não resolvido derruba o boot com mensagem
 * clara" (docs/task-0.4-identity-auth.md, perfil prod). The value is a CIDR list or a regular
 * expression (application-prod.yml), so an invalid regular expression is also unusable.
 *
 * <p>"Mensagem clara" is checked as: the message names the property and the environment variable,
 * which a generic placeholder or regex error would not both do.
 */
class TrustedProxiesGuardTest {

    private static final String ENVIRONMENT_VARIABLE = "TRUSTED_PROXIES";

    private final TrustedProxiesGuard guard = new TrustedProxiesGuard();

    private static MockEnvironment prodEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        return environment;
    }

    private Throwable postProcess(MockEnvironment environment) {
        return catchThrowable(() -> guard.postProcessEnvironment(environment, new SpringApplication()));
    }

    private Throwable postProcessProdWith(String trustedProxies) {
        return postProcess(prodEnvironment().withProperty(TrustedProxiesGuard.PROPERTY, trustedProxies));
    }

    @Test
    void shouldFailBootInProdWhenTrustedProxiesIsMissing() {
        Throwable thrown = postProcess(prodEnvironment());

        assertThat(thrown).isInstanceOf(RuntimeException.class);
        assertThat(thrown.getMessage()).contains(TrustedProxiesGuard.PROPERTY).contains(ENVIRONMENT_VARIABLE);
    }

    @Test
    void shouldFailBootInProdWhenTrustedProxiesIsEmpty() {
        Throwable thrown = postProcessProdWith("");

        assertThat(thrown).isInstanceOf(RuntimeException.class);
        assertThat(thrown.getMessage()).contains(TrustedProxiesGuard.PROPERTY).contains(ENVIRONMENT_VARIABLE);
    }

    @Test
    void shouldFailBootInProdWhenTrustedProxiesIsBlank() {
        Throwable thrown = postProcessProdWith("   ");

        assertThat(thrown).isInstanceOf(RuntimeException.class);
        assertThat(thrown.getMessage()).contains(TrustedProxiesGuard.PROPERTY).contains(ENVIRONMENT_VARIABLE);
    }

    @Test
    void shouldFailBootInProdWhenTrustedProxiesPlaceholderIsNotResolved() {
        Throwable thrown = postProcessProdWith("${TRUSTED_PROXIES}");

        assertThat(thrown).isInstanceOf(RuntimeException.class);
        assertThat(thrown.getMessage()).contains(TrustedProxiesGuard.PROPERTY).contains(ENVIRONMENT_VARIABLE);
    }

    @Test
    void shouldFailBootInProdWhenTrustedProxiesIsAnInvalidRegularExpression() {
        Throwable thrown = postProcessProdWith("10\\.0\\.0\\.(");

        assertThat(thrown).isInstanceOf(RuntimeException.class);
        assertThat(thrown.getMessage()).contains(TrustedProxiesGuard.PROPERTY).contains(ENVIRONMENT_VARIABLE);
    }

    @Test
    void shouldBootInProdWithCidrList() {
        Throwable thrown = postProcessProdWith("10.0.0.0/8, 192.168.1.10/32");

        assertThat(thrown).isNull();
    }

    @Test
    void shouldBootInProdWithRegularExpression() {
        Throwable thrown = postProcessProdWith("10\\.0\\.0\\.\\d{1,3}");

        assertThat(thrown).isNull();
    }

    @Test
    void shouldBootInProdWhenPlaceholderResolvesToValidValue() {
        MockEnvironment environment = prodEnvironment()
                .withProperty(TrustedProxiesGuard.PROPERTY, "${TRUSTED_PROXIES}")
                .withProperty(ENVIRONMENT_VARIABLE, "10\\.0\\.0\\.1");

        Throwable thrown = postProcess(environment);

        assertThat(thrown).isNull();
    }

    @Test
    void shouldNotRequireTrustedProxiesOutsideProd() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        Throwable thrown = postProcess(environment);

        assertThat(thrown).isNull();
    }
}
