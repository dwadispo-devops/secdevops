package ma.medidispo.pharmacy_service.pharmacy;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/** US15 (amélioration) : gardes déclarées par le pharmacien pour ses pharmacies. */
public final class MyDutyDtos {
    private MyDutyDtos() {}

    /** Une période de garde (bornes incluses). */
    public record MyDuty(UUID id, UUID pharmacyId, String pharmacyName, LocalDate startsOn, LocalDate endsOn) {}

    /** Corps de POST /api/pharmacies/me/gardes. */
    public record NewDuty(@NotNull UUID pharmacyId, @NotNull LocalDate startsOn, @NotNull LocalDate endsOn) {}
}
