package br.com.castel.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "br.com.castel")
public class CastelApplication {

    public static void main(String[] args) {
        SpringApplication.run(CastelApplication.class, args);
    }
}
