package ma.medidispo.pharmacy_service.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;

/** US14 (Kafka) – Mémorise les événements déjà traités pour ne jamais créer une notification en double. */
@Repository
public class EvenementTraiteRepository {

    private final JdbcTemplate jdbc;

    public EvenementTraiteRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** @return true si l'événement est nouveau (il est alors enregistré), false s'il a déjà été traité. */
    public boolean marquer(UUID eventId) {
        return jdbc.update(
            "INSERT INTO notification_events_traites (event_id) VALUES (?) ON CONFLICT (event_id) DO NOTHING",
            eventId) == 1;
    }
}
