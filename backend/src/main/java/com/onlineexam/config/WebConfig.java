package com.onlineexam.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Configures CORS to allow the Vite dev server (port 5173) to access the API.
 * In production, restrict allowedOrigins to the actual frontend domain.
 * Registers AuthInterceptor for API authentication.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final AuthInterceptor authInterceptor;

  public WebConfig(AuthInterceptor authInterceptor) {
    this.authInterceptor = authInterceptor;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
        .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*", "https://web.novo.ccwu.cc", "http://web.novo.ccwu.cc", "http://54.179.150.131")
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true)
        .maxAge(3600);
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(authInterceptor)
        .addPathPatterns("/api/**")
        .excludePathPatterns("/api/login", "/api/health");
  }

  /**
   * SPA fallback: for any non-API, non-static path, serve index.html
   * so Vue Router can handle the client-side route.
   */
  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/**")
        .addResourceLocations("classpath:/static/")
        .resourceChain(true)
        .addResolver(new PathResourceResolver() {
          @Override
          protected Resource getResource(String resourcePath, Resource location) {
            try {
              Resource requested = location.createRelative(resourcePath);
              if (requested.exists() && requested.isReadable()) {
                return requested;
              }
            } catch (Exception ignored) {}
            // SPA fallback: serve index.html for all non-API routes
            return new ClassPathResource("/static/index.html");
          }
        });
  }
}
