package br.com.castel.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

/**
 * "Boot falha alto, com mensagem clara, quando a configuração é perigosa: perfis dev e prod ativos
 * juntos" (docs/task-0.4-identity-auth.md). Exercised directly, without starting a context.
 */
class ProfileCombinationGuardTest {

    private final ProfileCombinationGuard guard = new ProfileCombinationGuard();

    private Throwable postProcess(String... activeProfiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        return catchThrowable(() -> guard.postProcessEnvironment(environment, new SpringApplication()));
    }

    @Test
    void shouldFailBootWhenDevAndProdProfilesAreActiveTogether() {
        Throwable thrown = postProcess("dev", "prod");

        assertThat(thrown).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldNameBothConflictingProfilesInTheFailureMessage() {
        Throwable thrown = postProcess("prod", "dev");

        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).contains("dev").contains("prod");
    }

    @Test
    void shouldFailBootWhenDevAndProdAreActiveAmongOtherProfiles() {
        Throwable thrown = postProcess("test", "dev", "prod");

        assertThat(thrown).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldBootWithOnlyDevProfile() {
        Throwable thrown = postProcess("dev");

        assertThat(thrown).isNull();
    }

    @Test
    void shouldBootWithOnlyProdProfile() {
        Throwable thrown = postProcess("prod");

        assertThat(thrown).isNull();
    }

    @Test
    void shouldBootWithoutActiveProfiles() {
        Throwable thrown = postProcess();

        assertThat(thrown).isNull();
    }
}
