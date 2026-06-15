package com.onlineexam.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configures CORS to allow the Vite dev server (port 5173) to access the API.
 * Registers AuthInterceptor for API authentication.
 *
 * Static resources are served by Spring Boot's default auto-configuration
 * (classpath:/static/, etc.). SPA fallback is handled by SpaFallbackController.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final AuthInterceptor authInterceptor;
  private final List<String> corsOrigins;

  public WebConfig(AuthInterceptor authInterceptor,
                   @Value("${cors.allowed-origins:http://localhost:*,http://127.0.0.1:*,https://web.novo.ccwu.cc,http://web.novo.ccwu.cc}") String corsOriginsStr) {
    this.authInterceptor = authInterceptor;
    this.corsOrigins = List.of(corsOriginsStr.split(","));
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
        .allowedOriginPatterns(corsOrigins.toArray(new String[0]))
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("Content-Type", "X-User-Id", "Authorization")
        .exposedHeaders("X-RateLimit-Remaining")
        .allowCredentials(true)
        .maxAge(3600);
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(authInterceptor)
        .addPathPatterns("/api/**")
        .excludePathPatterns("/api/login", "/api/health", "/api/bootstrap", "/api/ai/health");
  }

}
