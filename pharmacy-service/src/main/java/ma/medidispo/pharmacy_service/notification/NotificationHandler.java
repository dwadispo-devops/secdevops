package ma.medidispo.pharmacy_service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashSet;
import java.util.UUID;

/**
 * US14 – Traite un événement : crée les notifications dans le site (et l'e-mail PENDING pour l'outbox).
 * Appelé par le consommateur Kafka, ou directement quand Kafka est désactivé ou indisponible.
 * Idempotent : un événement déjà traité est ignoré.
 */
@Component
public class NotificationHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationHandler.class);

    private final NotificationRepository repository;
    private final EvenementTraiteRepository traites;

    public NotificationHandler(NotificationRepository repository, EvenementTraiteRepository traites) {
        this.repository = repository;
        this.traites = traites;
    }

    @Transactional
    public void traiter(NotificationEvent evenement) {
        if (!traites.marquer(evenement.id())) {
            log.info("Événement {} déjà traité : ignoré", evenement.id());
            return;
        }
        switch (evenement.type()) {
            case NOUVELLE_DEMANDE -> {
                if (evenement.pharmacies().isEmpty()) return;
                for (UUID pharmacien : new LinkedHashSet<>(repository.ownersOf(evenement.pharmacies()))) {
                    repository.insert(pharmacien, "NOUVELLE_DEMANDE", "Nouvelle demande de disponibilité",
                        "Un patient recherche des médicaments près de votre pharmacie. Répondez-lui depuis votre espace.",
                        "/pharmacien/demandes");
                }
            }
            case REPONSE_PHARMACIE -> repository.notifyPatientOfResponse(evenement.reference(), evenement.pharmacie());
            case RESERVATION -> repository.notifyReservation(evenement.reference());
            case RETRAIT -> repository.notifyPickup(evenement.reference());
        }
    }
}
