package ma.medidispo.pharmacy_service.admin;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class PharmacyAdminDtos {
    private PharmacyAdminDtos() {}

    public record PendingPharmacy(UUID id, String name, String address, String licenseNumber,
                                  OffsetDateTime createdAt) {}
}
