package ma.medidispo.pharmacy_service.review;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static ma.medidispo.pharmacy_service.review.ReviewDtos.*;

@Repository
public class ReviewRepository {
    private final JdbcTemplate jdbc;
    public ReviewRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final String SELECT = """
        SELECT r.id, p.id pharmacy_id, p.name pharmacy_name, a.status, r.created_at,
               r.prescription_id, a.price, a.comment, a.responded_at
        FROM availability_request_pharmacies a
        JOIN availability_requests r ON r.id=a.request_id
        JOIN pharmacies p ON p.id=a.pharmacy_id
        """;
    private static final RowMapper<Request> MAPPER = (rs, n) -> new Request(
        rs.getObject("id", UUID.class), rs.getObject("pharmacy_id", UUID.class),
        rs.getString("pharmacy_name"), rs.getString("status"),
        rs.getObject("created_at", OffsetDateTime.class), rs.getObject("prescription_id", UUID.class),
        rs.getBigDecimal("price"), rs.getString("comment"), rs.getObject("responded_at", OffsetDateTime.class));
    public List<Request> inbox(UUID owner) {
        return jdbc.query(SELECT + " WHERE p.owner_user_id=? ORDER BY r.created_at DESC, p.id LIMIT 200", MAPPER, owner);
    }
    public Optional<Request> assigned(UUID owner, UUID pharmacy, UUID request) {
        return jdbc.query(SELECT + " WHERE p.owner_user_id=? AND p.id=? AND r.id=?", MAPPER,
                          owner, pharmacy, request).stream().findFirst();
    }
    public List<Medication> medications(UUID request) {
        return jdbc.query("SELECT medication_name, quantity FROM availability_request_items WHERE request_id=? ORDER BY id",
                          (rs,n) -> new Medication(rs.getString(1), rs.getInt(2)), request);
    }
    public Optional<UUID> patient(UUID request) {
        return jdbc.query("SELECT patient_id FROM availability_requests WHERE id=?", (rs,n) -> rs.getObject(1,UUID.class), request)
            .stream().findFirst();
    }
    public int respond(UUID owner, UUID pharmacy, UUID request, Response response) {
        return jdbc.update("""
            UPDATE availability_request_pharmacies a SET status=?, price=?, comment=?, responded_at=now(), responded_by=?
            WHERE request_id=? AND pharmacy_id=? AND status='PENDING'
              AND EXISTS (SELECT 1 FROM pharmacies p WHERE p.id=a.pharmacy_id AND p.owner_user_id=?)
            """, response.availability().name(), response.price(), response.comment(), owner, request, pharmacy, owner);
    }
    public void lockRequest(UUID request) {
        jdbc.queryForObject("SELECT id FROM availability_requests WHERE id=? FOR UPDATE", UUID.class, request);
    }
    public int link(UUID patient, UUID request, UUID prescription) {
        return jdbc.update("""
            UPDATE availability_requests r SET prescription_id=? WHERE id=? AND patient_id=? AND prescription_id IS NULL
            AND NOT EXISTS (SELECT 1 FROM availability_request_pharmacies a WHERE a.request_id=r.id AND a.status<>'PENDING')
            """, prescription, request, patient);
    }
    public List<Request> responses(UUID patient, UUID request) {
        return jdbc.query(SELECT + " WHERE r.patient_id=? AND r.id=? ORDER BY p.name", MAPPER, patient, request);
    }
}
