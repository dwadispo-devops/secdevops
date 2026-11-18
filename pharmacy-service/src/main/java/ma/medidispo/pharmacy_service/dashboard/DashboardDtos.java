package ma.medidispo.pharmacy_service.dashboard;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class DashboardDtos {
    private DashboardDtos() {}

    public record RecentReservation(
        UUID id,
        UUID requestId,
        String pharmacyName,
        String status,
        BigDecimal price,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt
    ) {}

    public record Dashboard(
        long totalRequests,
        long pendingRequests,
        long respondedRequests,
        double responseRate,
        long totalReservations,
        long activeReservations,
        long pickedUpReservations,
        List<RecentReservation> recentReservations
    ) {}
}
