package ma.medidispo.pharmacy_service.dashboard;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

import static ma.medidispo.pharmacy_service.dashboard.DashboardDtos.*;

@Repository
public class DashboardRepository {
    private final JdbcTemplate jdbc;

    public DashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Dashboard findByOwner(UUID owner) {
        var stats = jdbc.queryForObject("""
            SELECT
                COUNT(a.request_id) AS total_requests,
                COUNT(*) FILTER (WHERE a.status = 'PENDING') AS pending_requests,
                COUNT(*) FILTER (WHERE a.status <> 'PENDING') AS responded_requests,
                COALESCE(
                    ROUND(
                        100.0 * COUNT(*) FILTER (WHERE a.status <> 'PENDING')
                        / NULLIF(COUNT(a.request_id), 0), 1
                    ), 0
                ) AS response_rate
            FROM pharmacies p
            LEFT JOIN availability_request_pharmacies a ON a.pharmacy_id = p.id
            WHERE p.owner_user_id = ?
            """,
            (rs, row) -> new Object[] {
                rs.getLong("total_requests"),
                rs.getLong("pending_requests"),
                rs.getLong("responded_requests"),
                rs.getDouble("response_rate")
            },
            owner
        );

        var reservations = jdbc.queryForObject("""
            SELECT
                COUNT(*) AS total_reservations,
                COUNT(*) FILTER (
                    WHERE s.status = 'ACTIVE' AND s.expires_at > now()
                ) AS active_reservations,
                COUNT(*) FILTER (WHERE s.status = 'RETIREE') AS picked_up_reservations
            FROM reservations s
            JOIN pharmacies p ON p.id = s.pharmacy_id
            WHERE p.owner_user_id = ?
            """,
            (rs, row) -> new long[] {
                rs.getLong("total_reservations"),
                rs.getLong("active_reservations"),
                rs.getLong("picked_up_reservations")
            },
            owner
        );

        List<RecentReservation> recent = jdbc.query("""
            SELECT
                s.id,
                s.request_id,
                p.name AS pharmacy_name,
                CASE
                    WHEN s.status = 'ACTIVE' AND s.expires_at <= now() THEN 'EXPIREE'
                    ELSE s.status
                END AS status,
                s.price,
                s.created_at,
                s.expires_at
            FROM reservations s
            JOIN pharmacies p ON p.id = s.pharmacy_id
            WHERE p.owner_user_id = ?
            ORDER BY s.created_at DESC
            LIMIT 5
            """,
            (rs, row) -> new RecentReservation(
                rs.getObject("id", UUID.class),
                rs.getObject("request_id", UUID.class),
                rs.getString("pharmacy_name"),
                rs.getString("status"),
                rs.getBigDecimal("price"),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("expires_at", java.time.OffsetDateTime.class)
            ),
            owner
        );

        return new Dashboard(
            (long) stats[0],
            (long) stats[1],
            (long) stats[2],
            (double) stats[3],
            reservations[0],
            reservations[1],
            reservations[2],
            recent
        );
    }
}
