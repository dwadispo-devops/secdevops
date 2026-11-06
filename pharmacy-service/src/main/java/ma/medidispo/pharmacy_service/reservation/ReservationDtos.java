package ma.medidispo.pharmacy_service.reservation;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class ReservationDtos {
    private ReservationDtos() {}
    /** status : ACTIVE, RETIREE, ANNULEE ou EXPIREE. */
    public record Reservation(UUID id, UUID requestId, UUID pharmacyId, String pharmacyName, String code,
                              String status, BigDecimal price, OffsetDateTime createdAt, OffsetDateTime expiresAt) {}
    public record CreateReservation(@NotNull UUID pharmacyId) {}
}
