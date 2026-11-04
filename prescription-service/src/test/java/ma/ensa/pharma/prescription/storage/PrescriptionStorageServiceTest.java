package ma.ensa.pharma.prescription.storage;

import ma.ensa.pharma.prescription.domain.DetectedFileType;
import ma.ensa.pharma.prescription.exception.PrescriptionException;
import ma.ensa.pharma.prescription.file.FileValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PrescriptionStorageServiceTest {

    /** Début d'un vrai fichier PNG (signature binaire). */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0};

    private final PrescriptionRepository prescriptions = mock(PrescriptionRepository.class);
    private final PrescriptionImageRepository images = mock(PrescriptionImageRepository.class);
    private final ImageEncryptionService encryption =
            new ImageEncryptionService(Base64.getEncoder().encodeToString(new byte[32]));

    private final PrescriptionStorageService service =
            new PrescriptionStorageService(prescriptions, images, new FileValidator(), encryption);

    private final UUID patient = UUID.randomUUID();

    @BeforeEach
    void saveReturnsArgument() {
        when(prescriptions.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void enregistreLOrdonnanceEtLImageChiffree() {
        Prescription saved = service.store(patient, PNG, "ordo.png", true);

        assertThat(saved.getPatientId()).isEqualTo(patient);
        assertThat(saved.getStatus()).isEqualTo(PrescriptionStatus.UPLOADED);
        assertThat(saved.getFileType()).isEqualTo(DetectedFileType.PNG);
        assertThat(saved.getSizeBytes()).isEqualTo(PNG.length);
        assertThat(saved.getConsentGivenAt()).isNotNull();

        ArgumentCaptor<PrescriptionImage> image = ArgumentCaptor.forClass(PrescriptionImage.class);
        verify(images).save(image.capture());
        assertThat(image.getValue().getPrescriptionId()).isEqualTo(saved.getId());
        assertThat(image.getValue().getEncryptedData()).isNotEqualTo(PNG); // jamais en clair
    }

    @Test
    void refuseSansConsentementSansRienEnregistrer() {
        assertThatThrownBy(() -> service.store(patient, PNG, "ordo.png", false))
                .isInstanceOf(PrescriptionException.class)
                .hasFieldOrPropertyWithValue("code", "CONSENT_REQUIRED");
        verifyNoInteractions(images);
        verify(prescriptions, never()).save(any());
    }

    @Test
    void refuseUnFichierPiegeSansRienEnregistrer() {
        byte[] exe = {'M', 'Z', 0, 0};   // un exécutable Windows renommé en .png
        assertThatThrownBy(() -> service.store(patient, exe, "ordo.png", true))
                .isInstanceOf(PrescriptionException.class);
        verifyNoInteractions(images);
        verify(prescriptions, never()).save(any());
    }

    @Test
    void rendLImageDechiffreeAuProprietaire() {
        Prescription p = new Prescription(patient, DetectedFileType.PNG, PNG.length);
        ImageEncryptionService.EncryptedData enc = encryption.encrypt(PNG);
        when(prescriptions.findById(p.getId())).thenReturn(Optional.of(p));
        when(images.findById(p.getId()))
                .thenReturn(Optional.of(new PrescriptionImage(p.getId(), enc.iv(), enc.cipherText())));

        PrescriptionStorageService.StoredImage image = service.loadImage(patient, p.getId());

        assertThat(image.data()).isEqualTo(PNG);
        assertThat(image.mediaType()).isEqualTo("image/png");
    }

    @Test
    void refuseLImageAUnAutrePatient() {
        Prescription p = new Prescription(patient, DetectedFileType.PNG, PNG.length);
        when(prescriptions.findById(p.getId())).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.loadImage(UUID.randomUUID(), p.getId()))
                .isInstanceOf(PrescriptionException.class)
                .hasFieldOrPropertyWithValue("code", "PRESCRIPTION_NOT_FOUND");
    }

    @Test
    void supprimeLImage() {
        Prescription p = new Prescription(patient, DetectedFileType.PNG, PNG.length);
        when(prescriptions.findById(p.getId())).thenReturn(Optional.of(p));

        service.deleteImage(patient, p.getId());

        verify(images).deleteById(p.getId());
        assertThat(p.hasImage()).isFalse();
        // Après suppression, l'image n'est plus accessible
        assertThatThrownBy(() -> service.loadImage(patient, p.getId())).isInstanceOf(PrescriptionException.class);
    }
}
