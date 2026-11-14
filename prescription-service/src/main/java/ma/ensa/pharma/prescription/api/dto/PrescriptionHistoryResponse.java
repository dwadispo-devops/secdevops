package ma.ensa.pharma.prescription.api.dto;

import ma.ensa.pharma.prescription.storage.Prescription;
import java.time.Instant;
import java.util.UUID;

public record PrescriptionHistoryResponse(
        UUID id,
        String status,
        String fileType,
        Instant createdAt,
        boolean imageAvailable
) {
    public static PrescriptionHistoryResponse from(Prescription prescription) {
        return new PrescriptionHistoryResponse(
                prescription.getId(),
                prescription.getStatus().name(),
                prescription.getFileType().name(),
                prescription.getCreatedAt(),
                prescription.hasImage()
        );
    }
}
