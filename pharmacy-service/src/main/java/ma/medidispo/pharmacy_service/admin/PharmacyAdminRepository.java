package ma.medidispo.pharmacy_service.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static ma.medidispo.pharmacy_service.admin.PharmacyAdminDtos.PendingPharmacy;

@Repository
public class PharmacyAdminRepository {
    private static final int MAX_PENDING = 100;
    private final JdbcTemplate jdbc;

    public PharmacyAdminRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PendingPharmacy> findPending() {
        return jdbc.query("""
            SELECT id, name, address, license_number, created_at
            FROM pharmacies
            WHERE status = 'PENDING'
            ORDER BY created_at, id
            LIMIT ?
            """, (rs, row) -> new PendingPharmacy(
                rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("address"),
                rs.getString("license_number"), rs.getObject("created_at", java.time.OffsetDateTime.class)),
            MAX_PENDING);
    }

    public Optional<PendingPharmacy> approvePending(UUID id) {
        return jdbc.query("""
            UPDATE pharmacies
            SET status = 'APPROVED'
            WHERE id = ? AND status = 'PENDING'
            RETURNING id, name, address, license_number, created_at
            """, (rs, row) -> new PendingPharmacy(
                rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("address"),
                rs.getString("license_number"), rs.getObject("created_at", java.time.OffsetDateTime.class)), id)
            .stream().findFirst();
    }

    public boolean exists(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM pharmacies WHERE id = ?)", Boolean.class, id));
    }
}
