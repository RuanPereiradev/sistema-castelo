package br.com.castel.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * {@link UserDetailsServiceAutoConfiguration} is excluded because authentication is by JWT only:
 * left in, it creates an in-memory user and logs a generated password at every start.
 */
@SpringBootApplication(scanBasePackages = "br.com.castel", exclude = UserDetailsServiceAutoConfiguration.class)
public class CastelApplication {

    public static void main(String[] args) {
        SpringApplication.run(CastelApplication.class, args);
    }
}
