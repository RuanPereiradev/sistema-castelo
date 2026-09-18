package br.com.castel.app.web;

import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Puts {@link RequestBodySizeLimitFilter} in front of the security chain.
 *
 * <p>Spring Security's chain registers at {@link SecurityFilterProperties#DEFAULT_FILTER_ORDER}; ordering
 * one step ahead of it is what makes an oversized body cost nothing: no BCrypt, no token parsing,
 * no controller.
 */
@Configuration
@EnableConfigurationProperties(RequestBodyLimitProperties.class)
public class RequestBodyLimitConfig {

    static final int REQUEST_BODY_SIZE_LIMIT_FILTER_ORDER = SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1;

    @Bean
    public FilterRegistrationBean<RequestBodySizeLimitFilter> requestBodySizeLimitFilterRegistration(
            RequestBodyLimitProperties properties, ObjectMapper objectMapper) {
        FilterRegistrationBean<RequestBodySizeLimitFilter> registration = new FilterRegistrationBean<>(
                new RequestBodySizeLimitFilter(properties.maxRequestBodyBytes(), objectMapper));
        registration.setOrder(REQUEST_BODY_SIZE_LIMIT_FILTER_ORDER);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
