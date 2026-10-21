package br.com.castel.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Turns on {@code @Scheduled} for the jobs of the modules: the expiry of the requests sent from the
 * QR code of the tables (task qr-table-ordering) is the first.
 *
 * <p>The scheduler is declared here, under the name {@code taskScheduler} that scheduling looks for
 * first. Without it, scheduling would pick the only {@code TaskScheduler} in the context, the one of
 * the STOMP broker, and a slow job would delay the heartbeats of the kitchen display.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("castel-job-");
        return scheduler;
    }
}
