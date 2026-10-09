package com.onlineexam.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Preserve raw decimal precision in untyped request Maps before domain integer validation. */
@Configuration
public class RequestNumberConfig {
  @Bean
  public Jackson2ObjectMapperBuilderCustomizer losslessUntypedNumbers() {
    return builder -> builder.featuresToEnable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
  }
}
