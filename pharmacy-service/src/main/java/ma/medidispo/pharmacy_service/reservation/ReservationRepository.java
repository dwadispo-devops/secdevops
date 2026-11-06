package ma.medidispo.pharmacy_service.reservation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import static ma.medidispo.pharmacy_service.reservation.ReservationDtos.*;

@Repository
public class ReservationRepository {
    private final JdbcTemplate jdbc;
    public ReservationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // Une réservation ACTIVE dont l'heure est dépassée est déjà présentée comme EXPIREE (RG03).
    private static final String SELECT = """
        SELECT s.id, s.request_id, s.pharmacy_id, p.name AS pharmacy_name, s.pickup_code,
               CASE WHEN s.status = 'ACTIVE' AND s.expires_at <= now() THEN 'EXPIREE' ELSE s.status END AS status,
               s.price, s.created_at, s.expires_at
        FROM reservations s JOIN pharmacies p ON p.id = s.pharmacy_id
        """;
    private static final RowMapper<Reservation> MAPPER = (rs, n) -> new Reservation(
        rs.getObject("id", UUID.class), rs.getObject("request_id", UUID.class), rs.getObject("pharmacy_id", UUID.class),
        rs.getString("pharmacy_name"), rs.getString("pickup_code"), rs.getString("status"), rs.getBigDecimal("price"),
        rs.getObject("created_at", OffsetDateTime.class), rs.getObject("expires_at", OffsetDateTime.class));

    /** RG04 : sérialise les réservations d'un même patient (verrou libéré à la fin de la transaction). */
    public void lockPatient(UUID patient) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", (ResultSetExtractor<Void>) rs -> null, patient.toString());
    }

    public int expireDue() {
        return jdbc.update("UPDATE reservations SET status = 'EXPIREE' WHERE status = 'ACTIVE' AND expires_at <= now()");
    }

    public boolean requestBelongs(UUID patient, UUID request) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM availability_requests WHERE id = ? AND patient_id = ?",
            Integer.class, request, patient);
        return n != null && n > 0;
    }

    public Optional<BigDecimal> availablePrice(UUID request, UUID pharmacy) {
        return jdbc.query("""
            SELECT price FROM availability_request_pharmacies
            WHERE request_id = ? AND pharmacy_id = ? AND status = 'AVAILABLE'
            """, (rs, n) -> rs.getBigDecimal(1), request, pharmacy).stream().findFirst();
    }

    public boolean activeForRequest(UUID request) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM reservations WHERE request_id = ? AND status = 'ACTIVE'",
            Integer.class, request);
        return n != null && n > 0;
    }

    public int activeCount(UUID patient) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM reservations WHERE patient_id = ? AND status = 'ACTIVE'",
            Integer.class, patient);
        return n == null ? 0 : n;
    }

    public boolean codeTaken(UUID pharmacy, String code) {
        Integer n = jdbc.queryForObject(
            "SELECT count(*) FROM reservations WHERE pharmacy_id = ? AND pickup_code = ? AND status = 'ACTIVE'",
            Integer.class, pharmacy, code);
        return n != null && n > 0;
    }

    public UUID insert(UUID request, UUID pharmacy, UUID patient, String code, BigDecimal price, int validiteSecondes) {
        return jdbc.queryForObject("""
            INSERT INTO reservations (request_id, pharmacy_id, patient_id, pickup_code, price, expires_at)
            VALUES (?, ?, ?, ?, ?, now() + (?::integer * interval '1 second'))
            RETURNING id
            """, UUID.class, request, pharmacy, patient, code, price, validiteSecondes);
    }

    public Optional<Reservation> find(UUID id) {
        return jdbc.query(SELECT + " WHERE s.id = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<Reservation> latest(UUID patient, UUID request) {
        return jdbc.query(SELECT + " WHERE s.patient_id = ? AND s.request_id = ? ORDER BY s.created_at DESC LIMIT 1",
            MAPPER, patient, request).stream().findFirst();
    }
}
