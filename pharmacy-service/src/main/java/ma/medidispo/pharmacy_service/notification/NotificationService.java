package ma.medidispo.pharmacy_service.notification;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.Collection;
import java.util.UUID;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static ma.medidispo.pharmacy_service.notification.NotificationDtos.*;

/**
 * US14 / EF24 – Notifications dans le site.
 * Les actions métier n'écrivent plus les notifications elles-mêmes : elles publient un NotificationEvent.
 *  - Kafka activé   : l'événement est envoyé dans Kafka après la transaction, puis traité par un consommateur.
 *  - Kafka désactivé : l'événement est traité tout de suite, dans la même transaction (comportement d'origine).
 * ENF05 : aucun nom de médicament dans les événements ni dans les messages.
 */
@Service
public class NotificationService {

    static final int MAX_AFFICHEES = 20;

    private final NotificationRepository repository;
    private final ApplicationEventPublisher evenements;

    public NotificationService(NotificationRepository repository, ApplicationEventPublisher evenements) {
        this.repository = repository;
        this.evenements = evenements;
    }

    /** Prévient les pharmaciens propriétaires des pharmacies contactées. */
    public void nouvelleDemande(UUID request, Collection<UUID> pharmacies) {
        evenements.publishEvent(NotificationEvent.nouvelleDemande(request, pharmacies));
    }

    public void reponsePharmacie(UUID request, UUID pharmacy) {
        evenements.publishEvent(NotificationEvent.reponsePharmacie(request, pharmacy));
    }

    public void reservation(UUID reservation) {
        evenements.publishEvent(NotificationEvent.reservation(reservation));
    }

    public void retrait(UUID reservation) {
        evenements.publishEvent(NotificationEvent.retrait(reservation));
    }

    public Notifications mes(UUID user) {
        return new Notifications(repository.recent(user, MAX_AFFICHEES), repository.unread(user));
    }

    public void lire(UUID user, UUID id) {
        if (repository.markRead(user, id) == 0) throw new ResponseStatusException(NOT_FOUND, "Notification introuvable");
    }

    public void toutLire(UUID user) { repository.markAllRead(user); }
}
