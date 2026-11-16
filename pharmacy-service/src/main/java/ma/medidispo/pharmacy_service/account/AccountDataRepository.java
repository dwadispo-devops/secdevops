package ma.medidispo.pharmacy_service.account;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** US20 / loi 09-08 : effacement des données d'un compte supprimé dans auth-service. */
@Repository
public class AccountDataRepository {

    private final JdbcTemplate jdbc;

    public AccountDataRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Patient : réservations, puis demandes (médicaments et réponses en cascade), puis notifications. */
    public int effacerPatient(UUID patientId) {
        int n = jdbc.update("DELETE FROM reservations WHERE patient_id = ?", patientId);
        n += jdbc.update("DELETE FROM availability_requests WHERE patient_id = ?", patientId);
        n += jdbc.update("DELETE FROM stock_alerts WHERE patient_id = ?", patientId);
        n += jdbc.update("DELETE FROM notifications WHERE user_id = ?", patientId);
        return n;
    }

    /**
     * Pharmacien : la pharmacie est détachée et redevient invisible tant qu'elle n'est pas revalidée
     * (OSM : UNVERIFIED comme à l'import ; inscrite : PENDING). Ses notifications sont supprimées.
     */
    public int detacherPharmacien(UUID pharmacistId) {
        int n = jdbc.update("""
            UPDATE pharmacies
            SET owner_user_id = NULL,
                status = CASE WHEN source = 'OSM' THEN 'UNVERIFIED' ELSE 'PENDING' END
            WHERE owner_user_id = ?
            """, pharmacistId);
        n += jdbc.update("DELETE FROM notifications WHERE user_id = ?", pharmacistId);
        return n;
    }
}
