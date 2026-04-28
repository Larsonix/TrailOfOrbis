package io.github.larsonix.trailoforbis.database;

import io.github.larsonix.trailoforbis.config.RPGConfig.DatabaseConfig;

import java.nio.file.Path;

/**
 * Supported database types with JDBC URL construction.
 *
 * <p>Centralizes database-specific configuration including JDBC URLs,
 * driver class names, and default ports.
 */
public enum DatabaseType {
    H2,
    MYSQL,
    POSTGRESQL;

    /**
     * @throws IllegalArgumentException if type is not recognized
     */
    public static DatabaseType fromString(String type) {
        if (type == null) {
            throw new IllegalArgumentException("Database type cannot be null");
        }
        return switch (type.toUpperCase()) {
            case "H2" -> H2;
            case "MYSQL" -> MYSQL;
            case "POSTGRESQL", "POSTGRES" -> POSTGRESQL;
            default -> throw new IllegalArgumentException(
                "Unsupported database type: " + type +
                ". Supported: H2, MySQL, PostgreSQL"
            );
        };
    }

    /** @param dataFolder plugin data folder (for H2 file path) */
    public String buildJdbcUrl(Path dataFolder, DatabaseConfig config) {
        return switch (this) {
            case H2 -> String.format(
                "jdbc:h2:%s/trailoforbis;MODE=MySQL",
                dataFolder.toAbsolutePath().toString().replace("\\", "/")
            );
            case MYSQL -> String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true",
                config.getHost(),
                config.getPort(),
                config.getDatabase()
            );
            case POSTGRESQL -> String.format(
                "jdbc:postgresql://%s:%d/%s",
                config.getHost(),
                config.getPort(),
                config.getDatabase()
            );
        };
    }

    public String getDriverClassName() {
        return switch (this) {
            case H2 -> "org.h2.Driver";
            case MYSQL -> "com.mysql.cj.jdbc.Driver";
            case POSTGRESQL -> "org.postgresql.Driver";
        };
    }

    /** @return 0 for file-based H2 */
    public int getDefaultPort() {
        return switch (this) {
            case H2 -> 0; // File-based, no port
            case MYSQL -> 3306;
            case POSTGRESQL -> 5432;
        };
    }
}
