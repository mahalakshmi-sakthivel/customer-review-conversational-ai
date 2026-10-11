package com.customerreview.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public FilterRegistrationBean<CorsFilter> customCorsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // Explicitly allowed local dev origins:
        // - Live Server / VS Code default: http://127.0.0.1:5500, http://localhost:5500
        // - Python / Node http-server default: http://localhost:8000, http://127.0.0.1:8000
        // - Frontend Vite / React dev servers: http://localhost:5173, http://localhost:3000
        config.setAllowedOrigins(List.of(
                "http://localhost:5500",
                "http://127.0.0.1:5500",
                "http://localhost:8000",
                "http://127.0.0.1:8000",
                "http://localhost:3000",
                "http://localhost:5173"
        ));
        
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization", "X-Tenant-Id", "Accept", "Origin", "X-Requested-With"));
        config.setExposedHeaders(List.of("X-Tenant-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);

        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        // Highest precedence ensures CORS headers are written BEFORE any security or tenant filters
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
