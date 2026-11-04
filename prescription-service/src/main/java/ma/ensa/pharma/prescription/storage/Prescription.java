package ma.ensa.pharma.prescription.storage;

import jakarta.persistence.*;
import ma.ensa.pharma.prescription.domain.DetectedFileType;

import java.time.Instant;
import java.util.UUID;

/**
 * Ordonnance envoyée par un patient (US06) – table "prescription".
 * L'image est stockée à part, chiffrée, dans PrescriptionImage : on ne la charge
 * jamais quand on lit seulement ces informations.
 */
@Entity
@Table(name = "prescription")
public class Prescription {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PrescriptionStatus status;

    /** Type réel détecté par FileValidator (JPEG, PNG, PDF). */
    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 10)
    private DetectedFileType fileType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** RG06 : date du consentement du patient. */
    @Column(name = "consent_given_at", nullable = false)
    private Instant consentGivenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Renseigné quand l'image a été supprimée (demande du patient, retrait ou expiration). */
    @Column(name = "image_deleted_at")
    private Instant imageDeletedAt;

    protected Prescription() {
        // Constructeur vide exigé par JPA
    }

    public Prescription(UUID patientId, DetectedFileType fileType, long sizeBytes) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.patientId = patientId;
        this.fileType = fileType;
        this.sizeBytes = sizeBytes;
        this.status = PrescriptionStatus.UPLOADED;
        this.consentGivenAt = now;
        this.createdAt = now;
    }

    /** Vrai si cette ordonnance appartient à ce patient. */
    public boolean belongsTo(UUID userId) {
        return patientId.equals(userId);
    }

    /** Vrai tant que l'image n'a pas été supprimée. */
    public boolean hasImage() {
        return imageDeletedAt == null;
    }

    public void markImageDeleted() {
        this.imageDeletedAt = Instant.now();
    }

    /** Type MIME à renvoyer quand on sert l'image au navigateur. */
    public String mediaType() {
        return switch (fileType) {
            case JPEG -> "image/jpeg";
            case PNG -> "image/png";
            case PDF -> "application/pdf";
            default -> "application/octet-stream";
        };
    }

    public UUID getId() { return id; }
    public UUID getPatientId() { return patientId; }
    public PrescriptionStatus getStatus() { return status; }
    public DetectedFileType getFileType() { return fileType; }
    public long getSizeBytes() { return sizeBytes; }
    public Instant getConsentGivenAt() { return consentGivenAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getImageDeletedAt() { return imageDeletedAt; }
}
