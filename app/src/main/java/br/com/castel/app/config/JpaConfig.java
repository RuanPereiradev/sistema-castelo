package br.com.castel.app.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Spring Boot only auto-detects entities and Spring Data repositories under the package of the
 * {@code @SpringBootApplication} class ({@code br.com.castel.app}), not under
 * {@code scanBasePackages}. This widens both to every module of {@code br.com.castel}.
 */
@Configuration
@EnableJpaRepositories(basePackages = "br.com.castel")
@EntityScan(basePackages = "br.com.castel")
public class JpaConfig {
}
