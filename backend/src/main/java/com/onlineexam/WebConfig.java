package com.onlineexam;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configures CORS to allow the Vite dev server (port 5173) to access the API.
 * In production, restrict allowedOrigins to the actual frontend domain.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
        .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*", "https://web.novo.ccwu.cc", "http://web.novo.ccwu.cc", "http://54.179.150.131")
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true)
        .maxAge(3600);
  }
}
