package br.com.desenvolvedorgustavolopes.moneyPilot;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Runs the migrations up to V11 in a separate schema, inserts users the way they exist before
 * account verification, and checks what V12 does to them.
 */
class MigrationV12Test extends AbstractIntegrationTest {

    private static final String SCHEMA = "migration_v12";

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        flyway("11").migrate();
    }

    // CA5 + CA8

    @Test
    void existingUsers_becomeVerified_withNormalizedEmails() {
        Instant firstCreatedAt = Instant.parse("2025-03-10T12:00:00Z");
        Instant secondCreatedAt = Instant.parse("2026-01-05T09:30:00Z");
        insertUser("Fulano@Exemplo.com", firstCreatedAt);
        insertUser("  ciclana@exemplo.com ", secondCreatedAt);

        flyway("12").migrate();

        List<String> emails = jdbc.queryForList(
                "SELECT email FROM " + SCHEMA + ".users ORDER BY id", String.class);
        assertEquals(List.of("fulano@exemplo.com", "ciclana@exemplo.com"), emails);
        assertEquals(firstCreatedAt, verifiedAt("fulano@exemplo.com"));
        assertEquals(secondCreatedAt, verifiedAt("ciclana@exemplo.com"));
    }

    @Test
    void afterMigration_emailsOutsideTheNormalizedForm_areRejected() {
        flyway("12").migrate();

        assertThrows(DataIntegrityViolationException.class, () -> insertUser("Fulano@Exemplo.com", Instant.now()));
        assertThrows(DataIntegrityViolationException.class, () -> insertUser(" fulano@exemplo.com", Instant.now()));
    }

    @Test
    void duplicatesIgnoringCase_failTheMigration_withoutChangingAnything() {
        insertUser("Fulano@Exemplo.com", Instant.parse("2025-03-10T12:00:00Z"));
        insertUser("fulano@exemplo.com", Instant.parse("2025-04-10T12:00:00Z"));

        assertThrows(RuntimeException.class, () -> flyway("12").migrate());

        List<String> emails = jdbc.queryForList(
                "SELECT email FROM " + SCHEMA + ".users ORDER BY id", String.class);
        assertEquals(List.of("Fulano@Exemplo.com", "fulano@exemplo.com"), emails);
        assertFalse(columnExists("users", "email_verified_at"));
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .target(target)
                .load();
    }

    private void insertUser(String email, Instant createdAt) {
        jdbc.update("INSERT INTO " + SCHEMA + ".users (email, password_hash, name, created_at) VALUES (?, ?, ?, ?)",
                email, "$2a$10$" + "x".repeat(53), "Pessoa", Timestamp.from(createdAt));
    }

    private Instant verifiedAt(String email) {
        return jdbc.queryForObject("SELECT email_verified_at FROM " + SCHEMA + ".users WHERE email = ?",
                Timestamp.class, email).toInstant();
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ? AND column_name = ?
                """, Integer.class, SCHEMA, table, column);
        return count != null && count > 0;
    }
}
