package ma.medidispo.pharmacy_service.notification;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import static ma.medidispo.pharmacy_service.notification.NotificationDtos.*;

/** US14 – Chaque utilisateur connecté (patient ou pharmacien) ne voit que ses propres notifications. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service) { this.service = service; }
    private UUID user(Authentication auth) { return (UUID) auth.getPrincipal(); }

    @GetMapping("/me")
    public Notifications mine(Authentication auth) { return service.mes(user(auth)); }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(Authentication auth, @PathVariable UUID id) { service.lire(user(auth), id); }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(Authentication auth) { service.toutLire(user(auth)); }
}
