package ma.medidispo.pharmacy_service.notification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class NotificationDtos {
    private NotificationDtos() {}
    public record Notification(UUID id, String type, String title, String message, String link,
                               OffsetDateTime createdAt, boolean read) {}
    public record Notifications(List<Notification> items, int unread) {}
}
