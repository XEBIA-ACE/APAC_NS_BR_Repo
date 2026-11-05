package com.xebia.ace.accountmanagement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/** AC-1 / T005: the service starts under the test profile and its health check responds. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AccountManagementServiceApplicationTests {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void contextLoads() {}

  @Test
  void healthEndpointRespondsUp() {
    ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("\"status\":\"UP\"")
        .doesNotContain("components")
        .doesNotContain("details");
  }

  @Test
  void livenessAndReadinessProbesRespondUp() {
    assertThat(restTemplate.getForEntity("/actuator/health/liveness", String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            restTemplate.getForEntity("/actuator/health/readiness", String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void nonPublicEndpointsRequireAuthentication() {
    assertThat(restTemplate.getForEntity("/actuator/env", String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(restTemplate.getForEntity("/api/accounts", String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void openApiDocsArePublished() {
    assertThat(restTemplate.getForEntity("/v3/api-docs", String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }
}
