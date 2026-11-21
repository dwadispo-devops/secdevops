package ma.medidispo.pharmacy_service.alert;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static ma.medidispo.pharmacy_service.alert.StockAlertDtos.*;

/** US22 : accès aux alertes, toujours filtré par le patient (sauf pour le déclenchement). */
@Repository
public class StockAlertRepository {

    private static final RowMapper<StockAlert> MAPPER = (rs, i) -> new StockAlert(
        rs.getObject("id", UUID.class), rs.getString("medication_code"), rs.getString("medication_name"),
        rs.getInt("radius_m"), rs.getString("status"),
        rs.getObject("created_at", OffsetDateTime.class), rs.getObject("triggered_at", OffsetDateTime.class),
        rs.getObject("pharmacy_id", UUID.class), rs.getString("pharmacy_name"), rs.getString("pharmacy_address"));

    private final JdbcTemplate jdbc;

    public StockAlertRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<StockAlert> list(UUID patientId) {
        return jdbc.query("""
            SELECT a.id, a.medication_code, a.medication_name, a.radius_m, a.status, a.created_at, a.triggered_at,
                   p.id AS pharmacy_id, p.name AS pharmacy_name, p.address AS pharmacy_address
            FROM stock_alerts a
            LEFT JOIN pharmacies p ON p.id = a.triggered_pharmacy_id
            WHERE a.patient_id = ?
            ORDER BY a.created_at DESC
            """, MAPPER, patientId);
    }

    public int countActive(UUID patientId) {
        return jdbc.queryForObject("SELECT count(*) FROM stock_alerts WHERE patient_id = ? AND status = 'ACTIVE'",
            Integer.class, patientId);
    }

    public UUID insert(UUID patientId, NewAlert a, int radius) {
        return jdbc.queryForObject("""
            INSERT INTO stock_alerts (patient_id, medication_code, medication_name, latitude, longitude, radius_m)
            VALUES (?, ?, ?, ?, ?, ?) RETURNING id
            """, UUID.class, patientId, a.medicationCode(), a.medicationName().trim(), a.latitude(), a.longitude(), radius);
    }

    public int delete(UUID patientId, UUID alertId) {
        return jdbc.update("DELETE FROM stock_alerts WHERE id = ? AND patient_id = ?", alertId, patientId);
    }

    /**
     * Déclenche, en une seule requête, les alertes actives de ce médicament dont le rayon contient la pharmacie
     * (pharmacie validée uniquement). Renvoie les patients à prévenir. Une alerte ne se déclenche qu'une fois.
     */
    public List<UUID> trigger(UUID pharmacyId, String medicationCode) {
        return jdbc.queryForList("""
            WITH declenchees AS (
                UPDATE stock_alerts a
                SET status = 'DECLENCHEE', triggered_at = now(), triggered_pharmacy_id = p.id
                FROM pharmacies p
                WHERE p.id = ? AND p.status = 'APPROVED'
                  AND a.status = 'ACTIVE' AND a.medication_code = ?
                  AND ST_DWithin(a.location, p.location, a.radius_m)
                RETURNING a.patient_id
            )
            SELECT DISTINCT patient_id FROM declenchees
            """, UUID.class, pharmacyId, medicationCode);
    }
}
