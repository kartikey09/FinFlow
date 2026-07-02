package io.finflow.query.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the Vite dev server (default {@code http://localhost:3000}) to hit the
 * API without CORS errors. The dashboard also configures a Vite proxy so this
 * is belt-and-suspenders — CORS covers direct browser calls, the proxy covers
 * calls through {@code /api}. In production these would be served same-origin
 * behind a reverse proxy and neither would be needed.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String allowedOrigins;

    public CorsConfig(@Value("${finflow.cors.allowed-origins}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "OPTIONS");
    }
}
