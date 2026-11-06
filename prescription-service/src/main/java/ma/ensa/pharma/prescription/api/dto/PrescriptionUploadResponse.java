package ma.ensa.pharma.prescription.api.dto;

import ma.ensa.pharma.prescription.storage.Prescription;

import java.time.Instant;
import java.util.UUID;

/** Réponse de l'US06 : l'ordonnance est enregistrée ; l'US07 pourra la lire avec cet id. */
public record PrescriptionUploadResponse(
        UUID id,
        String status,
        String fileType,
        long sizeBytes,
        Instant createdAt
) {
    public static PrescriptionUploadResponse from(Prescription p) {
        return new PrescriptionUploadResponse(
                p.getId(), p.getStatus().name(), p.getFileType().name(), p.getSizeBytes(), p.getCreatedAt());
    }
}
