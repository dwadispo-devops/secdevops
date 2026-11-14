package ma.medidispo.pharmacy_service.pharmacy;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static ma.medidispo.pharmacy_service.pharmacy.MyDutyDtos.MyDuty;

/** US15 (amélioration) : accès aux gardes, toujours filtré par le propriétaire de la pharmacie. */
@Repository
public class MyDutyRepository {

    private static final String SELECT = """
        SELECT d.id, d.pharmacy_id, p.name, d.starts_on, d.ends_on
        FROM pharmacy_duties d
        JOIN pharmacies p ON p.id = d.pharmacy_id
        """;

    private static final RowMapper<MyDuty> MAPPER = (rs, i) -> new MyDuty(
        rs.getObject("id", UUID.class),
        rs.getObject("pharmacy_id", UUID.class),
        rs.getString("name"),
        rs.getObject("starts_on", LocalDate.class),
        rs.getObject("ends_on", LocalDate.class));

    private final JdbcTemplate jdbc;

    public MyDutyRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<MyDuty> list(UUID owner) {
        return jdbc.query(SELECT + " WHERE p.owner_user_id = ? ORDER BY d.starts_on DESC, p.name", MAPPER, owner);
    }

    public boolean owns(UUID owner, UUID pharmacyId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM pharmacies WHERE id = ? AND owner_user_id = ?)",
            Boolean.class, pharmacyId, owner));
    }

    /** Vrai si une garde existante de la pharmacie chevauche [debut, fin]. */
    public boolean overlaps(UUID pharmacyId, LocalDate debut, LocalDate fin) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM pharmacy_duties
                           WHERE pharmacy_id = ? AND starts_on <= ? AND ends_on >= ?)
            """, Boolean.class, pharmacyId, fin, debut));
    }

    public MyDuty insert(UUID pharmacyId, LocalDate debut, LocalDate fin) {
        UUID id = jdbc.queryForObject(
            "INSERT INTO pharmacy_duties (pharmacy_id, starts_on, ends_on) VALUES (?, ?, ?) RETURNING id",
            UUID.class, pharmacyId, debut, fin);
        return jdbc.queryForObject(SELECT + " WHERE d.id = ?", MAPPER, id);
    }

    /** @return 1 si supprimée, 0 si la garde n'existe pas ou n'appartient pas à ce pharmacien. */
    public int delete(UUID owner, UUID dutyId) {
        return jdbc.update("""
            DELETE FROM pharmacy_duties d USING pharmacies p
            WHERE d.id = ? AND d.pharmacy_id = p.id AND p.owner_user_id = ?
            """, dutyId, owner);
    }
}
