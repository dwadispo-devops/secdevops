package ma.ensa.pharma.prescription.storage;

import ma.ensa.pharma.prescription.account.PrescriptionAccountDeletion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(PrescriptionAccountDeletion.class)
class PrescriptionPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired JdbcTemplate jdbc;
    @Autowired PrescriptionRepository prescriptions;
    @Autowired FamilyProfileRepository profils;
    @Autowired PrescriptionAccountDeletion effacement;

    private final Instant maintenant = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private UUID ordonnance(UUID patient, Instant creeLe, UUID profil) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO prescription (id, patient_id, status, file_type, size_bytes, consent_given_at, created_at, family_profile_id) VALUES (?, ?, 'UPLOADED', 'JPEG', 1024, ?, ?, ?)",
            id, patient, Timestamp.from(creeLe), Timestamp.from(creeLe), profil);
        return id;
    }

    private void image(UUID ordonnance) {
        jdbc.update("INSERT INTO prescription_image (prescription_id, iv, encrypted_data) VALUES (?, ?, ?)",
            ordonnance, new byte[] {1, 2, 3}, new byte[] {4, 5, 6});
    }

    private UUID profil(UUID patient, String libelle, Instant supprimeLe) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO family_profile (id, patient_id, label, created_at, deleted_at) VALUES (?, ?, ?, ?, ?)",
            id, patient, libelle, Timestamp.from(maintenant), supprimeLe == null ? null : Timestamp.from(supprimeLe));
        return id;
    }

    private int compte(String table, String colonne, UUID valeur) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + colonne + " = ?", Integer.class, valeur);
    }

    @Test
    void lesMigrationsFlywayCorrespondentAuxEntites() {
        Integer migrations = jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);
        assertThat(migrations).isGreaterThanOrEqualTo(3);
        assertThat(prescriptions.count()).isZero();
    }

    @Test
    void lHistoriqueDuPatientEstTrieDuPlusRecentAuPlusAncien() {
        UUID patient = UUID.randomUUID();
        UUID ancienne = ordonnance(patient, maintenant.minus(2, ChronoUnit.DAYS), null);
        UUID recente = ordonnance(patient, maintenant, null);
        ordonnance(UUID.randomUUID(), maintenant, null);

        assertThat(prescriptions.findAllByPatientIdOrderByCreatedAtDesc(patient))
            .extracting(Prescription::getId)
            .containsExactly(recente, ancienne);
    }

    @Test
    void lesProfilsFamilleActifsSontDistinctsEtSansDoublon() {
        UUID patient = UUID.randomUUID();
        UUID enfant = profil(patient, "Enfant", null);
        profil(patient, "Ancien proche", maintenant);
        UUID pourEnfant = ordonnance(patient, maintenant, enfant);
        ordonnance(patient, maintenant, null);

        assertThat(profils.findAllByPatientIdAndDeletedAtIsNullOrderByCreatedAtAsc(patient))
            .extracting(FamilyProfile::getId).containsExactly(enfant);
        assertThat(profils.existsByPatientIdAndDeletedAtIsNullAndLabelIgnoreCase(patient, "ENFANT")).isTrue();
        assertThat(prescriptions.findAllByPatientIdAndFamilyProfileIdOrderByCreatedAtDesc(patient, enfant))
            .extracting(Prescription::getId).containsExactly(pourEnfant);
        assertThatThrownBy(() -> profil(patient, "  enfant ", null))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void laSuppressionDuCompteEffaceToutesLesDonneesDuPatientSeulement() {
        UUID patient = UUID.randomUUID(), autre = UUID.randomUUID();
        UUID proche = profil(patient, "Parent", null);
        UUID ordo = ordonnance(patient, maintenant, proche);
        image(ordo);
        UUID ordoAutre = ordonnance(autre, maintenant, null);
        image(ordoAutre);

        effacement.compteSupprime(autre, "PHARMACIEN");
        assertThat(compte("prescription", "patient_id", autre)).isEqualTo(1);

        effacement.compteSupprime(patient, "PATIENT");

        assertThat(compte("prescription", "patient_id", patient)).isZero();
        assertThat(compte("prescription_image", "prescription_id", ordo)).isZero();
        assertThat(compte("family_profile", "patient_id", patient)).isZero();
        assertThat(compte("prescription", "patient_id", autre)).isEqualTo(1);
        assertThat(compte("prescription_image", "prescription_id", ordoAutre)).isEqualTo(1);
    }
}
