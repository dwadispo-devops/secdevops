package ma.medidispo.pharmacy_service.notification;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.UUID;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static ma.medidispo.pharmacy_service.notification.NotificationDtos.*;

/**
 * US14 / EF24 – Notifications dans le site.
 * Appelée dans la même transaction que l'événement : pas d'événement sans notification, ni l'inverse.
 * ENF05 : aucun nom de médicament dans les messages.
 */
@Service
public class NotificationService {
    static final int MAX_AFFICHEES = 20;
    private final NotificationRepository repository;
    public NotificationService(NotificationRepository repository) { this.repository = repository; }

    /** Prévient, une seule fois chacun, les pharmaciens propriétaires des pharmacies contactées. */
    public void nouvelleDemande(UUID request, Collection<UUID> pharmacies) {
        for (UUID pharmacien : new LinkedHashSet<>(repository.ownersOf(pharmacies))) {
            repository.insert(pharmacien, "NOUVELLE_DEMANDE", "Nouvelle demande de disponibilité",
                "Un patient recherche des médicaments près de votre pharmacie. Répondez-lui depuis votre espace.",
                "/pharmacien/demandes");
        }
    }

    public void reponsePharmacie(UUID request, UUID pharmacy) { repository.notifyPatientOfResponse(request, pharmacy); }

    public void reservation(UUID reservation) { repository.notifyReservation(reservation); }

    public void retrait(UUID reservation) { repository.notifyPickup(reservation); }

    public Notifications mes(UUID user) {
        return new Notifications(repository.recent(user, MAX_AFFICHEES), repository.unread(user));
    }

    public void lire(UUID user, UUID id) {
        if (repository.markRead(user, id) == 0) throw new ResponseStatusException(NOT_FOUND, "Notification introuvable");
    }

    public void toutLire(UUID user) { repository.markAllRead(user); }
}
