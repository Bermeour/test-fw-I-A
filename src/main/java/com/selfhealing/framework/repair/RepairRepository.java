package com.selfhealing.framework.repair;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;

/**
 * Caché local de reparaciones de locators en SQLite.
 *
 * <h3>Ciclo de vida de una entrada:</h3>
 * <ol>
 *   <li>{@link #saveOrUpdate} — se crea o actualiza cuando el servicio devuelve una reparación exitosa.</li>
 *   <li>{@link #findApprovedRepair} — se consulta antes de llamar al servicio; respeta TTL.</li>
 *   <li>{@link #touch} — se llama cuando la entrada de caché sigue siendo válida (incrementa times_seen).</li>
 *   <li>{@link #reject} — se llama cuando el selector cacheado ya no funciona en el DOM actual.</li>
 * </ol>
 */
public class RepairRepository {

    private final String dbUrl;

    public RepairRepository(String dbUrl) {
        this.dbUrl = dbUrl;
        init();
        migrate();
    }

    // ── Inicialización ────────────────────────────────────────────────────────

    private void init() {
        String createTable =
            "CREATE TABLE IF NOT EXISTS locator_repairs (" +
            "  id             INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  app            TEXT NOT NULL," +
            "  page_url       TEXT NOT NULL," +
            "  original_type  TEXT NOT NULL," +
            "  original_value TEXT NOT NULL," +
            "  repaired_type  TEXT NOT NULL," +
            "  repaired_value TEXT NOT NULL," +
            "  score          INTEGER NOT NULL," +
            "  reason         TEXT," +
            "  times_seen     INTEGER NOT NULL DEFAULT 1," +
            "  created_at     TEXT NOT NULL," +
            "  last_seen      TEXT NOT NULL," +
            "  status         TEXT NOT NULL DEFAULT 'APPROVED'," +
            "  reject_reason  TEXT" +
            ")";

        String createUniqueIndex =
            "CREATE UNIQUE INDEX IF NOT EXISTS ux_locator_repairs" +
            " ON locator_repairs (app, page_url, original_type, original_value, repaired_type, repaired_value)";

        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            try (PreparedStatement ps = conn.prepareStatement(createTable))       { ps.execute(); }
            try (PreparedStatement ps = conn.prepareStatement(createUniqueIndex)) { ps.execute(); }
        } catch (Exception e) {
            throw new RuntimeException("Error initializing SQLite", e);
        }
    }

    private void migrate() {
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            safeAlter(conn, "ALTER TABLE locator_repairs ADD COLUMN status TEXT NOT NULL DEFAULT 'APPROVED'");
            safeAlter(conn, "ALTER TABLE locator_repairs ADD COLUMN reject_reason TEXT");
        } catch (Exception e) {
            throw new RuntimeException("Error migrating SQLite", e);
        }
    }

    private void safeAlter(Connection conn, String sql) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
        } catch (Exception ignore) { /* columna ya existe */ }
    }

    // ── Escritura ─────────────────────────────────────────────────────────────

    /**
     * Inserta una nueva reparación o actualiza la existente si ya hay una con la misma clave.
     * Al actualizar, incrementa {@code times_seen}, refresca {@code last_seen} y resetea el status a APPROVED.
     */
    public void saveOrUpdate(String app,
                             String pageUrl,
                             String originalType,
                             String originalValue,
                             SuggestedLocator repaired) {
        String now = LocalDateTime.now().toString();
        String sql =
            "INSERT INTO locator_repairs" +
            " (app, page_url, original_type, original_value," +
            "  repaired_type, repaired_value, score, reason," +
            "  times_seen, created_at, last_seen, status, reject_reason)" +
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, 'APPROVED', NULL)" +
            " ON CONFLICT(app, page_url, original_type, original_value, repaired_type, repaired_value)" +
            " DO UPDATE SET" +
            "   times_seen    = times_seen + 1," +
            "   last_seen     = excluded.last_seen," +
            "   score         = excluded.score," +
            "   reason        = excluded.reason," +
            "   status        = 'APPROVED'," +
            "   reject_reason = NULL";

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

    /**
     * Actualiza {@code times_seen} y {@code last_seen} de una entrada existente.
     * Llamar cuando el caché fue usado exitosamente para mantener la entrada "fresca".
     */
    public void touch(String app,
                      String pageUrl,
                      String originalType,
                      String originalValue,
                      String repairedType,
                      String repairedValue) {
        String sql =
            "UPDATE locator_repairs" +
            "   SET times_seen = times_seen + 1, last_seen = ?" +
            " WHERE app = ? AND page_url = ?" +
            "   AND original_type = ? AND original_value = ?" +
            "   AND repaired_type = ? AND repaired_value = ?";

        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, LocalDateTime.now().toString());
            ps.setString(2, app);
            ps.setString(3, pageUrl);
            ps.setString(4, originalType);
            ps.setString(5, originalValue);
            ps.setString(6, repairedType);
            ps.setString(7, repairedValue);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Error touching repair entry", e);
        }
    }

    /**
     * Marca una entrada como REJECTED cuando el selector cacheado ya no funciona en el DOM.
     * La entrada queda en la tabla para auditoría pero no se devuelve en consultas futuras.
     *
     * @param reason motivo del rechazo (ej: "selector no encontrado en DOM")
     */
    public void reject(String app,
                       String pageUrl,
                       String originalType,
                       String originalValue,
                       String repairedType,
                       String repairedValue,
                       String reason) {
        String sql =
            "UPDATE locator_repairs" +
            "   SET status = 'REJECTED', reject_reason = ?" +
            " WHERE app = ? AND page_url = ?" +
            "   AND original_type = ? AND original_value = ?" +
            "   AND repaired_type = ? AND repaired_value = ?";

        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reason);
            ps.setString(2, app);
            ps.setString(3, pageUrl);
            ps.setString(4, originalType);
            ps.setString(5, originalValue);
            ps.setString(6, repairedType);
            ps.setString(7, repairedValue);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Error rejecting repair entry", e);
        }
    }

    // ── Lectura ───────────────────────────────────────────────────────────────

    /**
     * Busca una reparación aprobada para el selector dado, respetando TTL y score mínimo.
     *
     * @param app           nombre de la aplicación
     * @param pageUrl       URL o identificador de la página actual
     * @param originalType  tipo del selector original ("xpath" | "css")
     * @param originalValue valor del selector original
     * @param minScore      score mínimo para considerar la entrada confiable (recomendado: 80)
     * @param ttlDays       días máximos desde el último uso antes de considerar expirada la entrada
     * @return la reparación aprobada más reutilizada, o {@code null} si no hay ninguna válida
     */
    public SuggestedLocator findApprovedRepair(String app,
                                               String pageUrl,
                                               String originalType,
                                               String originalValue,
                                               int minScore,
                                               int ttlDays) {
        // Calculamos el cutoff en Java para evitar dependencia de funciones datetime de SQLite
        String cutoff = LocalDateTime.now().minusDays(ttlDays).toString();

        String sql =
            "SELECT repaired_type, repaired_value, score, reason" +
            " FROM locator_repairs" +
            " WHERE app = ?" +
            "   AND page_url = ?" +
            "   AND original_type = ?" +
            "   AND original_value = ?" +
            "   AND score >= ?" +
            "   AND status = 'APPROVED'" +
            "   AND last_seen >= ?" +
            " ORDER BY times_seen DESC, last_seen DESC" +
            " LIMIT 1";

        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, app);
            ps.setString(2, pageUrl);
            ps.setString(3, originalType);
            ps.setString(4, originalValue);
            ps.setInt(5, minScore);
            ps.setString(6, cutoff);

            ResultSet rs = ps.executeQuery();
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

    /**
     * Sobrecarga con TTL por defecto de 7 días — para compatibilidad con código existente.
     */
    public SuggestedLocator findApprovedRepair(String app,
                                               String pageUrl,
                                               String originalType,
                                               String originalValue,
                                               int minScore) {
        return findApprovedRepair(app, pageUrl, originalType, originalValue, minScore, 7);
    }

    /**
     * Indica si la página tiene al menos una reparación aprobada con score >= 80.
     * Útil para saber si una página ya fue "sanada" antes sin cargar los selectores.
     */
    public boolean hasSuccessfulRepairs(String app, String pageUrl) {
        String sql =
            "SELECT 1 FROM locator_repairs" +
            " WHERE app = ?" +
            "   AND page_url = ?" +
            "   AND score >= 80" +
            "   AND status = 'APPROVED'" +
            " LIMIT 1";

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
