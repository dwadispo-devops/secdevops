package ma.medidispo.auth_service.internal;

import ma.medidispo.auth_service.user.User;
import ma.medidispo.auth_service.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * US14 – Coordonnées d'un utilisateur pour l'envoi des e-mails de notification.
 * Appel de service à service uniquement : non exposé par la gateway (/internal) et protégé
 * par un jeton partagé (INTERNAL_API_TOKEN). Ne renvoie que le strict nécessaire (loi 09-08).
 */
@RestController
public class InternalUserController {
    private final UserRepository users;
    private final byte[] jeton;

    public InternalUserController(UserRepository users, @Value("${app.internal-token:${INTERNAL_API_TOKEN:}}") String jeton) {
        this.users = users;
        this.jeton = jeton.getBytes(StandardCharsets.UTF_8);
    }

    public record Contact(String email, String firstName) {}

    @GetMapping("/internal/users/{id}/contact")
    public ResponseEntity<Contact> contact(@PathVariable UUID id,
                                           @RequestHeader(value = "X-Internal-Token", required = false) String fourni) {
        // Comparaison en temps constant ; refus si aucun jeton n'est configuré.
        if (jeton.length == 0 || fourni == null
                || !MessageDigest.isEqual(jeton, fourni.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return users.findById(id).filter(User::isEnabled)
            .map(u -> ResponseEntity.ok(new Contact(u.getEmail(), u.getFirstName())))
            .orElse(ResponseEntity.notFound().build());
    }
}
