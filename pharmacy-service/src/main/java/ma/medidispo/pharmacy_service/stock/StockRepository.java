package ma.medidispo.pharmacy_service.stock;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StockRepository {

    private final JdbcTemplate jdbcTemplate;

    public StockRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(UUID pharmacyId, String medicationCode, StockStatus status) {
        jdbcTemplate.update("""
            INSERT INTO pharmacy_stock (pharmacy_id, medication_code, status)
            VALUES (?, ?, ?)
            ON CONFLICT (pharmacy_id, medication_code)
            DO UPDATE SET status = EXCLUDED.status,
                          updated_at = CURRENT_TIMESTAMP
            """, pharmacyId, medicationCode, status.name());
    }

    /** US22 : statut actuel (null si le médicament n'a jamais été déclaré par cette pharmacie). */
    public StockStatus currentStatus(UUID pharmacyId, String medicationCode) {
        List<String> statuts = jdbcTemplate.queryForList(
            "SELECT status FROM pharmacy_stock WHERE pharmacy_id = ? AND medication_code = ? FOR UPDATE",
            String.class, pharmacyId, medicationCode);
        return statuts.isEmpty() ? null : StockStatus.valueOf(statuts.get(0));
    }
}
