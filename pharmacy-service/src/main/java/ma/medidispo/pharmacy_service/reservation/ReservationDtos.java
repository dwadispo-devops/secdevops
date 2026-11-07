package ma.medidispo.pharmacy_service.reservation;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ReservationDtos {
    private ReservationDtos() {}

    /** status : ACTIVE, RETIREE, ANNULEE ou EXPIREE. */
    public record Reservation(UUID id, UUID requestId, UUID pharmacyId, String pharmacyName, String code,
                              String status, BigDecimal price, OffsetDateTime createdAt, OffsetDateTime expiresAt) {}

    public record CreateReservation(@NotNull UUID pharmacyId) {}

    /** Médicament demandé dans la réservation (minimisation des données). */
    public record MedicationItem(String name, int quantity) {}

    /** Vue retournée au pharmacien lors de la recherche par code de retrait (US13, minimisation loi 09-08 : pas d'identité patient). */
    public record PickupView(UUID id, String code, OffsetDateTime expiresAt, BigDecimal price, List<MedicationItem> medications) {}

    /** Réponse de confirmation lors de la validation du retrait. */
    public record PickupConfirmation(UUID id, String status, OffsetDateTime pickedUpAt) {}
}
