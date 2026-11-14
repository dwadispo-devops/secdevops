package ma.medidispo.pharmacy_service.notification;

import java.util.UUID;

public final class EmailDtos {
    private EmailDtos() {}
    /** Notification dont l'e-mail reste à envoyer. */
    public record EmailEnAttente(UUID id, UUID userId, String title, String message, String link) {}
    /** Coordonnées renvoyées par auth-service (strict nécessaire). */
    public record Contact(String email, String firstName) {}
}
