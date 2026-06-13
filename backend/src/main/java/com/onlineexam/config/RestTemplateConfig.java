package com.onlineexam.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate configuration for AI API calls with timeout settings
 */
@Configuration
public class RestTemplateConfig {

  @Value("${ai.timeout-ms:15000}")
  private int timeoutMs;

  @Bean
  public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
      .setConnectTimeout(Duration.ofMillis(timeoutMs))
      .setReadTimeout(Duration.ofMillis(timeoutMs))
      .build();
  }
}
