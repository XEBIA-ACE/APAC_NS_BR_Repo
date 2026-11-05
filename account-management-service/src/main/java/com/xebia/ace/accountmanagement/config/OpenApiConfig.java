package com.xebia.ace.accountmanagement.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata. No business API is defined by SCAFFOLD-SVC-001. */
@Configuration
public class OpenApiConfig {

  @Bean
  OpenAPI serviceOpenApi(
      @Value("${spring.application.name}") String applicationName,
      @Value("${app.version:0.0.1-SNAPSHOT}") String version) {
    return new OpenAPI().info(new Info().title(applicationName).version(version));
  }
}
