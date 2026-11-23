package ma.ensa.pharma.prescription.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * US20 / loi 09-08 : droit à l'effacement des données de santé.
 * Supprime toutes les ordonnances d'un patient dont le compte a été supprimé ;
 * les images chiffrées (prescription_image) partent en cascade (ON DELETE CASCADE).
 * Rejouable sans risque. Aucune donnée personnelle dans les logs.
 */
@Service
public class PrescriptionAccountDeletion {

    static final String SQL = "DELETE FROM prescription WHERE patient_id = ?";
    static final String PROFILE_SQL = "DELETE FROM family_profile WHERE patient_id = ?";
    private static final Logger log = LoggerFactory.getLogger(PrescriptionAccountDeletion.class);

    private final JdbcTemplate jdbc;

    public PrescriptionAccountDeletion(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void compteSupprime(UUID userId, String role) {
        if (!"PATIENT".equals(role)) {
            return; // seuls les patients ont des ordonnances
        }
        int n = jdbc.update(SQL, userId);
        jdbc.update(PROFILE_SQL, userId);
        log.info("Compte supprimé (PATIENT) : {} ordonnance(s) effacée(s), images comprises", n);
    }
}
