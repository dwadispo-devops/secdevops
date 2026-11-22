package ma.medidispo.pharmacy_service.notification;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * US14 – Événement de notification, publié dans Kafka (topic dwadispo.notifications).
 * ENF05 : il ne contient que des identifiants, jamais de nom de médicament.
 *
 * @param id          identifiant unique de l'événement (sert à l'idempotence)
 * @param type        ce qui s'est passé
 * @param reference   la demande (NOUVELLE_DEMANDE, REPONSE_PHARMACIE) ou la réservation (RESERVATION, RETRAIT)
 * @param pharmacie   la pharmacie qui a répondu (REPONSE_PHARMACIE seulement)
 * @param pharmacies  les pharmacies contactées (NOUVELLE_DEMANDE seulement)
 */
public record NotificationEvent(UUID id, Type type, UUID reference, UUID pharmacie, List<UUID> pharmacies) {

    public enum Type { NOUVELLE_DEMANDE, REPONSE_PHARMACIE, RESERVATION, RETRAIT }

    public NotificationEvent {
        pharmacies = pharmacies == null ? List.of() : List.copyOf(pharmacies);
    }

    public static NotificationEvent nouvelleDemande(UUID request, Collection<UUID> pharmacies) {
        return new NotificationEvent(UUID.randomUUID(), Type.NOUVELLE_DEMANDE, request, null, List.copyOf(pharmacies));
    }

    public static NotificationEvent reponsePharmacie(UUID request, UUID pharmacy) {
        return new NotificationEvent(UUID.randomUUID(), Type.REPONSE_PHARMACIE, request, pharmacy, List.of());
    }

    public static NotificationEvent reservation(UUID reservation) {
        return new NotificationEvent(UUID.randomUUID(), Type.RESERVATION, reservation, null, List.of());
    }

    public static NotificationEvent retrait(UUID reservation) {
        return new NotificationEvent(UUID.randomUUID(), Type.RETRAIT, reservation, null, List.of());
    }
}
