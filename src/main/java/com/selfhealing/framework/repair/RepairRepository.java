package com.selfhealing.framework.repair;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;

/**
 * Almacena el historial de reparaciones de locators en SQLite local.
 *
 * Uso:
 *   RepairRepository repo = new RepairRepository("jdbc:sqlite:repair-history.db");
 *   repo.saveOrUpdate(app, pageUrl, "xpath", "//button", suggested);
 *   SuggestedLocator cached = repo.findApprovedRepair(app, pageUrl, "xpath", "//button", 80);
 */
public class RepairRepository {

    private final String dbUrl;

    public RepairRepository(String dbUrl) {
        this.dbUrl = dbUrl;
        init();
        migrate();
    }

    private void init() {
        String createTable = """
            CREATE TABLE IF NOT EXISTS locator_repairs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                app TEXT NOT NULL,
                page_url TEXT NOT NULL,
                original_type TEXT NOT NULL,
                original_value TEXT NOT NULL,
                repaired_type TEXT NOT NULL,
                repaired_value TEXT NOT NULL,
                score INTEGER NOT NULL,
                reason TEXT,
                times_seen INTEGER NOT NULL DEFAULT 1,
                created_at TEXT NOT NULL,
                last_seen TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'APPROVED',
                reject_reason TEXT
            )
        """;

        String createUniqueIndex = """
            CREATE UNIQUE INDEX IF NOT EXISTS ux_locator_repairs
            ON locator_repairs (
                app, page_url,
                original_type, original_value,
                repaired_type, repaired_value
            )
        """;

        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            try (PreparedStatement ps = conn.prepareStatement(createTable))       { ps.execute(); }
            try (PreparedStatement ps = conn.prepareStatement(createUniqueIndex)) { ps.execute(); }
        } catch (Exception e) {
            throw new RuntimeException("Error initializing SQLite", e);
        }
    }

    private void migrate() {
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "ALTER TABLE locator_repairs ADD COLUMN status TEXT NOT NULL DEFAULT 'APPROVED'"
            )) { ps.execute(); } catch (Exception ignore) {}

            try (PreparedStatement ps = conn.prepareStatement(
                    "ALTER TABLE locator_repairs ADD COLUMN reject_reason TEXT"
            )) { ps.execute(); } catch (Exception ignore) {}
        } catch (Exception e) {
            throw new RuntimeException("Error migrating SQLite", e);
        }
    }

    public void saveOrUpdate(String app,
                             String pageUrl,
                             String originalType,
                             String originalValue,
                             SuggestedLocator repaired) {

        String now = LocalDateTime.now().toString();
        String sql = """
            INSERT INTO locator_repairs
            (app, page_url, original_type, original_value,
             repaired_type, repaired_value, score, reason,
             times_seen, created_at, last_seen, status, reject_reason)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, 'APPROVED', NULL)
            ON CONFLICT(app, page_url, original_type, original_value, repaired_type, repaired_value)
            DO UPDATE SET
              times_seen = times_seen + 1,
              last_seen  = excluded.last_seen,
              score      = excluded.score,
              reason     = excluded.reason,
              status     = 'APPROVED',
              reject_reason = NULL
        """;

        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, app);
            ps.setString(2, pageUrl);
            ps.setString(3, originalType);
            ps.setString(4, originalValue);
            ps.setString(5, repaired.getType());
            ps.setString(6, repaired.getValue());
            ps.setInt(7, repaired.getScore());
            ps.setString(8, repaired.getReason());
            ps.setString(9, now);
            ps.setString(10, now);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Error saving/updating repair", e);
        }
    }

    public SuggestedLocator findApprovedRepair(String app,
                                               String pageUrl,
                                               String originalType,
                                               String originalValue,
                                               int minScore) {
        String sql = """
            SELECT repaired_type, repaired_value, score, reason
            FROM locator_repairs
            WHERE app = ?
              AND page_url = ?
              AND original_type = ?
              AND original_value = ?
              AND score >= ?
              AND status = 'APPROVED'
            ORDER BY times_seen DESC, last_seen DESC
            LIMIT 1
        """;

        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, app);
            ps.setString(2, pageUrl);
            ps.setString(3, originalType);
            ps.setString(4, originalValue);
            ps.setInt(5, minScore);

            var rs = ps.executeQuery();
            if (rs.next()) {
                SuggestedLocator s = new SuggestedLocator();
                s.setType(rs.getString("repaired_type"));
                s.setValue(rs.getString("repaired_value"));
                s.setScore(rs.getInt("score"));
                s.setReason(rs.getString("reason"));
                return s;
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Error querying repair cache", e);
        }
    }

    public boolean hasSuccessfulRepairs(String app, String pageUrl) {
        String sql = """
            SELECT 1
            FROM locator_repairs
            WHERE app = ?
              AND page_url = ?
              AND score >= 80
              AND status = 'APPROVED'
            LIMIT 1
        """;

        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, app);
            ps.setString(2, pageUrl);
            return ps.executeQuery().next();
        } catch (Exception e) {
            throw new RuntimeException("Error checking successful repairs", e);
        }
    }
}
