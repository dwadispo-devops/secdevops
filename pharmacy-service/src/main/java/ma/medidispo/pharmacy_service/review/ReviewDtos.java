package ma.medidispo.pharmacy_service.review;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ReviewDtos {
    private ReviewDtos() {}
    public record Request(UUID requestId, UUID pharmacyId, String pharmacyName, String status,
                          OffsetDateTime createdAt, UUID prescriptionId, BigDecimal price,
                          String comment, OffsetDateTime respondedAt) {}
    public record Medication(String name, int quantity) {}
    public record Detail(Request request, List<Medication> medications) {}
    public enum Availability { AVAILABLE, UNAVAILABLE }
    public record Response(@NotNull Availability availability,
                           @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
                           @Size(max = 1000) String comment) {}
    public record Link(@NotNull UUID prescriptionId) {}
    public record Prescription(boolean imageAvailable, String mediaType, String ocrText,
                               List<RecognizedMedication> medications) {}
    public record RecognizedMedication(String rawText, String name, String dosage, String status) {}
}
