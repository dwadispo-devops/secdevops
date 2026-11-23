package ma.ensa.pharma.prescription.api.dto;

import ma.ensa.pharma.prescription.storage.Prescription;
import java.time.Instant;
import java.util.UUID;

public record PrescriptionHistoryResponse(
        UUID id,
        String status,
        String fileType,
        Instant createdAt,
        boolean imageAvailable,
        UUID profileId,
        String profileLabel
) {
    public PrescriptionHistoryResponse(UUID id, String status, String fileType, Instant createdAt, boolean imageAvailable) {
        this(id, status, fileType, createdAt, imageAvailable, null, null);
    }

    public static PrescriptionHistoryResponse from(Prescription prescription) {
        return new PrescriptionHistoryResponse(
                prescription.getId(),
                prescription.getStatus().name(),
                prescription.getFileType().name(),
                prescription.getCreatedAt(),
                prescription.hasImage(),
                prescription.getFamilyProfile() == null ? null : prescription.getFamilyProfile().getId(),
                prescription.getFamilyProfile() == null ? null : prescription.getFamilyProfile().getLabel()
        );
    }
}
