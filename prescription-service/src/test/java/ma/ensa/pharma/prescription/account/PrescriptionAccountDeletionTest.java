package ma.ensa.pharma.prescription.account;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** US20 / loi 09-08 : effacement des ordonnances d'un compte supprimé. */
class PrescriptionAccountDeletionTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PrescriptionAccountDeletion deletion = new PrescriptionAccountDeletion(jdbc);
    private final KafkaAccountListener listener = new KafkaAccountListener(deletion);
    private final UUID patient = UUID.randomUUID();

    @Test void effaceToutesLesOrdonnancesDuPatient() {
        deletion.compteSupprime(patient, "PATIENT");
        verify(jdbc).update(PrescriptionAccountDeletion.SQL, patient);
        verify(jdbc).update(PrescriptionAccountDeletion.PROFILE_SQL, patient);
    }

    @Test void neToucheRienPourUnPharmacien() {
        deletion.compteSupprime(UUID.randomUUID(), "PHARMACIEN");
        verifyNoInteractions(jdbc);
    }

    @Test void lEvenementKafkaDeclencheLEffacement() throws Exception {
        listener.recevoir("{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"COMPTE_SUPPRIME\",\"userId\":\""
            + patient + "\",\"role\":\"PATIENT\",\"occurredAt\":\"2026-11-15T21:00:00Z\"}");
        verify(jdbc).update(PrescriptionAccountDeletion.SQL, patient);
        verify(jdbc).update(PrescriptionAccountDeletion.PROFILE_SQL, patient);
    }

    @Test void ignoreLesAutresEvenements() throws Exception {
        listener.recevoir("{\"type\":\"AUTRE\",\"userId\":\"" + patient + "\",\"role\":\"PATIENT\"}");
        verifyNoInteractions(jdbc);
    }
}
