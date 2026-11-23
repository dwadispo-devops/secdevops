package ma.medidispo.pharmacy_service.rating;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import static ma.medidispo.pharmacy_service.rating.PharmacyRatingDtos.*;

@Repository
public class PharmacyRatingRepository {
    private final JdbcTemplate jdbc;
    public PharmacyRatingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Eligibility(boolean pickedUp) {}

    public Optional<Eligibility> eligibility(UUID patient, UUID request, UUID reservation) {
        return jdbc.query("""
            SELECT status = 'RETIREE' AND picked_up_at IS NOT NULL AS picked_up
            FROM reservations WHERE id = ? AND request_id = ? AND patient_id = ?
            FOR UPDATE
            """, (rs, n) -> new Eligibility(rs.getBoolean("picked_up")), reservation, request, patient)
            .stream().findFirst();
    }

    public Optional<Rating> find(UUID reservation) {
        return jdbc.query("SELECT id, reservation_id, stars, created_at FROM pharmacy_ratings WHERE reservation_id = ?",
            (rs, n) -> new Rating(rs.getObject("id", UUID.class), rs.getObject("reservation_id", UUID.class),
                rs.getInt("stars"), rs.getObject("created_at", OffsetDateTime.class)), reservation).stream().findFirst();
    }

    public Optional<Rating> insert(UUID reservation, int stars) {
        // La contrainte UNIQUE reste la garantie finale, y compris entre plusieurs instances.
        return jdbc.query("""
            INSERT INTO pharmacy_ratings (reservation_id, stars) VALUES (?, ?)
            ON CONFLICT (reservation_id) DO NOTHING
            RETURNING id, reservation_id, stars, created_at
            """, (rs, n) -> new Rating(rs.getObject("id", UUID.class), rs.getObject("reservation_id", UUID.class),
                rs.getInt("stars"), rs.getObject("created_at", OffsetDateTime.class)), reservation, stars)
            .stream().findFirst();
    }
}
