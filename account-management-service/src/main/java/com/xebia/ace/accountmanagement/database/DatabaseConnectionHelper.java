package com.xebia.ace.accountmanagement.database;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Thin helper around the environment-configured {@link DataSource} (see {@code spring.datasource.*}
 * in {@code application.yml}).
 *
 * <p>Provides a connectivity check and a log-safe description of the connection. Credentials are
 * never read or logged by this class; JDBC URLs are masked before they are emitted.
 */
@Component
public class DatabaseConnectionHelper {

  private static final Logger log = LoggerFactory.getLogger(DatabaseConnectionHelper.class);
  private static final int VALIDATION_TIMEOUT_SECONDS = 5;

  private static final Pattern USER_INFO = Pattern.compile("//[^/@]*@");
  private static final Pattern SENSITIVE_PARAM =
      Pattern.compile("(?i)((?:password|passwd|pwd|user|username|sslpassword)=)[^&;]*");

  private final DataSource dataSource;

  public DatabaseConnectionHelper(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /** Returns {@code true} if a connection can be obtained and validated. */
  public boolean isConnectionValid() {
    try (Connection connection = dataSource.getConnection()) {
      return connection.isValid(VALIDATION_TIMEOUT_SECONDS);
    } catch (SQLException e) {
      log.warn("Database connectivity check failed: {}", e.getClass().getSimpleName());
      return false;
    }
  }

  /** Returns a description of the current connection that is safe to log. */
  public ConnectionInfo describe() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      DatabaseMetaData meta = connection.getMetaData();
      return new ConnectionInfo(
          meta.getDatabaseProductName(),
          meta.getDatabaseProductVersion(),
          maskJdbcUrl(meta.getURL()));
    }
  }

  @EventListener(ApplicationReadyEvent.class)
  void logConnectionOnStartup() {
    try {
      ConnectionInfo info = describe();
      log.info(
          "Database connection established: {} {} at {}",
          info.productName(),
          info.productVersion(),
          info.maskedUrl());
    } catch (SQLException e) {
      log.warn("Unable to describe database connection: {}", e.getClass().getSimpleName());
    }
  }

  /** Removes credentials embedded in a JDBC URL (user-info segment or query parameters). */
  public static String maskJdbcUrl(String url) {
    if (url == null) {
      return null;
    }
    String masked = USER_INFO.matcher(url).replaceFirst("//***@");
    return SENSITIVE_PARAM.matcher(masked).replaceAll("$1***");
  }

  /** Log-safe connection metadata. */
  public record ConnectionInfo(String productName, String productVersion, String maskedUrl) {}
}
