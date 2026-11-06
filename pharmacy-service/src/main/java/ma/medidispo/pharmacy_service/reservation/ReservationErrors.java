package ma.medidispo.pharmacy_service.reservation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

/** Renvoie le message métier (en français) au front, uniquement pour les réservations. */
@RestControllerAdvice(assignableTypes = ReservationController.class)
class ReservationErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> handle(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
            .body(Map.of("message", e.getReason() == null ? "Erreur" : e.getReason()));
    }
}
