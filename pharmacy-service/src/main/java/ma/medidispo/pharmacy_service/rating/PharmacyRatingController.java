package ma.medidispo.pharmacy_service.rating;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.UUID;
import static ma.medidispo.pharmacy_service.rating.PharmacyRatingDtos.*;

@RestController
@RequestMapping("/api/availability-requests/{request}/reservations/{reservation}/rating")
public class PharmacyRatingController {
    private final PharmacyRatingService service;
    public PharmacyRatingController(PharmacyRatingService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Rating create(Authentication auth, @PathVariable UUID request, @PathVariable UUID reservation,
                         @Valid @RequestBody CreateRating body) {
        return service.create((UUID) auth.getPrincipal(), request, reservation, body.stars());
    }

    @GetMapping
    public ResponseEntity<Rating> get(Authentication auth, @PathVariable UUID request, @PathVariable UUID reservation) {
        var rating = service.get((UUID) auth.getPrincipal(), request, reservation);
        // 204 distingue une réservation autorisée sans note d'une réservation introuvable (404).
        return rating == null ? ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()
            : ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rating);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> error(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", ex.getReason() == null ? "Erreur" : ex.getReason()));
    }
}
