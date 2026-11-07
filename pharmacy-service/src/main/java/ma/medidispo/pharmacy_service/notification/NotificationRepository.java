package ma.medidispo.pharmacy_service.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import static ma.medidispo.pharmacy_service.notification.NotificationDtos.*;

@Repository
public class NotificationRepository {
    private final JdbcTemplate jdbc;
    public NotificationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final RowMapper<Notification> MAPPER = (rs, n) -> new Notification(
        rs.getObject("id", UUID.class), rs.getString("type"), rs.getString("title"), rs.getString("message"),
        rs.getString("link"), rs.getObject("created_at", OffsetDateTime.class), rs.getObject("read_at") != null);

    /** Propriétaires (distincts) des pharmacies données ; les pharmacies sans pharmacien inscrit sont ignorées. */
    public List<UUID> ownersOf(java.util.Collection<UUID> pharmacies) {
        if (pharmacies.isEmpty()) return List.of();
        String marques = String.join(",", java.util.Collections.nCopies(pharmacies.size(), "?"));
        return jdbc.queryForList("SELECT DISTINCT owner_user_id FROM pharmacies WHERE owner_user_id IS NOT NULL AND id IN ("
            + marques + ")", UUID.class, pharmacies.toArray());
    }

    public void insert(UUID user, String type, String title, String message, String link) {
        jdbc.update("INSERT INTO notifications (user_id, type, title, message, link) VALUES (?, ?, ?, ?, ?)",
            user, type, title, message, link);
    }

    /** Le patient est prévenu de la réponse d'une pharmacie (prix seulement, jamais les médicaments). */
    public int notifyPatientOfResponse(UUID request, UUID pharmacy) {
        return jdbc.update("""
            INSERT INTO notifications (user_id, type, title, message, link)
            SELECT r.patient_id, 'REPONSE_PHARMACIE',
                   CASE WHEN a.status = 'AVAILABLE' THEN 'Médicaments disponibles' ELSE 'Réponse d''une pharmacie' END,
                   CASE WHEN a.status = 'AVAILABLE'
                        THEN p.name || ' a vos médicaments (' || to_char(a.price, 'FM999999990.00') || ' DH).'
                        ELSE p.name || ' n''a pas vos médicaments.' END,
                   '/suivi-demande/' || r.id
            FROM availability_request_pharmacies a
            JOIN availability_requests r ON r.id = a.request_id
            JOIN pharmacies p ON p.id = a.pharmacy_id
            WHERE a.request_id = ? AND a.pharmacy_id = ?
            """, request, pharmacy);
    }

    /** Réservation : le patient reçoit l'heure limite, le pharmacien est prévenu qu'un code va lui être présenté. */
    public void notifyReservation(UUID reservation) {
        jdbc.update("""
            INSERT INTO notifications (user_id, type, title, message, link)
            SELECT s.patient_id, 'RESERVATION_CONFIRMEE', 'Réservation confirmée',
                   'Présentez votre code de retrait chez ' || p.name || ' avant '
                     || to_char(s.expires_at AT TIME ZONE 'Africa/Casablanca', 'HH24:MI') || '.',
                   '/suivi-demande/' || s.request_id
            FROM reservations s JOIN pharmacies p ON p.id = s.pharmacy_id
            WHERE s.id = ?
            """, reservation);
        jdbc.update("""
            INSERT INTO notifications (user_id, type, title, message, link)
            SELECT p.owner_user_id, 'NOUVELLE_RESERVATION', 'Nouvelle réservation',
                   'Un patient a réservé chez ' || p.name || '. Il présentera un code de retrait au comptoir.',
                   '/pharmacien/demandes'
            FROM reservations s JOIN pharmacies p ON p.id = s.pharmacy_id
            WHERE s.id = ? AND p.owner_user_id IS NOT NULL
            """, reservation);
    }

    /** US13 + US14 : le patient est prévenu que le retrait a été validé au comptoir. */
    public void notifyPickup(UUID reservation) {
        jdbc.update("""
            INSERT INTO notifications (user_id, type, title, message, link)
            SELECT s.patient_id, 'RETRAIT_CONFIRME', 'Médicaments retirés',
                   'Votre retrait chez ' || p.name || ' a été validé. Merci d''avoir utilisé DwaDispo.',
                   '/suivi-demande/' || s.request_id
            FROM reservations s JOIN pharmacies p ON p.id = s.pharmacy_id
            WHERE s.id = ?
            """, reservation);
    }

    public List<Notification> recent(UUID user, int limit) {
        return jdbc.query("""
            SELECT id, type, title, message, link, created_at, read_at FROM notifications
            WHERE user_id = ? ORDER BY created_at DESC LIMIT ?
            """, MAPPER, user, limit);
    }

    public int unread(UUID user) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND read_at IS NULL",
            Integer.class, user);
        return n == null ? 0 : n;
    }

    /** 0 si la notification n'existe pas ou appartient à un autre utilisateur. */
    public int markRead(UUID user, UUID id) {
        return jdbc.update("UPDATE notifications SET read_at = coalesce(read_at, now()) WHERE id = ? AND user_id = ?", id, user);
    }

    public int markAllRead(UUID user) {
        return jdbc.update("UPDATE notifications SET read_at = now() WHERE user_id = ? AND read_at IS NULL", user);
    }
}
