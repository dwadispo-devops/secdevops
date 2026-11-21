package ma.medidispo.pharmacy_service.alert;

import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** US22 : alertes de retour en stock. */
public final class StockAlertDtos {
    private StockAlertDtos() {}

    /** Une alerte, avec la pharmacie qui l'a déclenchée le cas échéant. */
    public record StockAlert(UUID id, String medicationCode, String medicationName, int radiusM, String status,
                             OffsetDateTime createdAt, OffsetDateTime triggeredAt,
                             UUID pharmacyId, String pharmacyName, String pharmacyAddress) {}

    /** Corps de POST /api/alertes. Le rayon vaut 5 km par défaut. */
    public record NewAlert(
            @NotBlank @Pattern(regexp = "[0-9]{1,13}", message = "Code médicament invalide") String medicationCode,
            @NotBlank @Size(max = 200) String medicationName,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @Min(100) @Max(20000) Integer radiusM) {}

    /** Événement publié quand un médicament repasse « en stock » dans une pharmacie. */
    public record StockAvailableEvent(UUID id, UUID pharmacyId, String medicationCode) {
        public static StockAvailableEvent of(UUID pharmacyId, String medicationCode) {
            return new StockAvailableEvent(UUID.randomUUID(), pharmacyId, medicationCode);
        }
    }
}
