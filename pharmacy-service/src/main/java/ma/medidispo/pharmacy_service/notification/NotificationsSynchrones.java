package ma.medidispo.pharmacy_service.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** US14 – Mode sans Kafka (tests, ou notifications.kafka.enabled=false) : traitement immédiat, même transaction. */
@Component
@ConditionalOnProperty(name = "notifications.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class NotificationsSynchrones {

    private final NotificationHandler handler;

    public NotificationsSynchrones(NotificationHandler handler) { this.handler = handler; }

    @EventListener
    public void surEvenement(NotificationEvent evenement) { handler.traiter(evenement); }
}
