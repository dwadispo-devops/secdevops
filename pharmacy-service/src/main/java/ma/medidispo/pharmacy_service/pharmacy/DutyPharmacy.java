package ma.medidispo.pharmacy_service.pharmacy;

import java.util.UUID;

public record DutyPharmacy(
        UUID id,
        String name,
        String address,
        String phone,
        String openingHours,
        double latitude,
        double longitude,
        Integer distanceM,   // null si le client n'a pas envoyé sa position
        String dutyUntil     // ISO yyyy-MM-dd, dernier jour de garde
) {}
