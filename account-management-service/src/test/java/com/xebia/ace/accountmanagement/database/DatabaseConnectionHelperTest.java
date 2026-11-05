package com.xebia.ace.accountmanagement.database;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseConnectionHelperTest {

  @Autowired private DatabaseConnectionHelper helper;

  @Test
  void connectsUsingConfiguredDataSource() throws Exception {
    assertThat(helper.isConnectionValid()).isTrue();
    assertThat(helper.describe().productName()).isEqualTo("H2");
  }

  @Test
  void masksCredentialsInJdbcUrls() {
    assertThat(
            DatabaseConnectionHelper.maskJdbcUrl(
                "jdbc:postgresql://db.internal:5432/accounts?user=admin&password=s3cr3t&ssl=true"))
        .isEqualTo("jdbc:postgresql://db.internal:5432/accounts?user=***&password=***&ssl=true");
    assertThat(DatabaseConnectionHelper.maskJdbcUrl("jdbc:postgresql://admin:s3cr3t@db:5432/x"))
        .isEqualTo("jdbc:postgresql://***@db:5432/x");
    assertThat(DatabaseConnectionHelper.maskJdbcUrl("jdbc:postgresql://localhost:5432/x"))
        .isEqualTo("jdbc:postgresql://localhost:5432/x");
    assertThat(DatabaseConnectionHelper.maskJdbcUrl(null)).isNull();
  }
}
