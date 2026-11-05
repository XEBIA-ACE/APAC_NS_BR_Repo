package com.xebia.ace.accountmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Entrypoint for the Account Management Service scaffold (SCAFFOLD-SVC-001).
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} is excluded so Spring Security does not generate
 * and log a default in-memory user password; authentication is out of scope for this story.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableCaching
public class AccountManagementServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(AccountManagementServiceApplication.class, args);
  }
}
