package ma.medidispo.pharmacy_service.reservation;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import static ma.medidispo.pharmacy_service.reservation.ReservationDtos.*;

/**
 * Endpoints de réservation :
 * - Côté patient : création et consultation (US12)
 * - Côté pharmacien : recherche par code et validation de retrait (US13)
 */
@RestController
public class ReservationController {
    private final ReservationService service;
    public ReservationController(ReservationService service) { this.service = service; }
    private UUID user(Authentication auth) { return (UUID) auth.getPrincipal(); }

    // --- US12 : Patient ---

    @PostMapping("/api/availability-requests/{request}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public Reservation reserve(Authentication auth, @PathVariable UUID request, @Valid @RequestBody CreateReservation body) {
        return service.reserve(user(auth), request, body.pharmacyId());
    }

    @GetMapping("/api/availability-requests/{request}/reservation")
    public Reservation current(Authentication auth, @PathVariable UUID request) {
        return service.current(user(auth), request);
    }

    // --- US13 : Pharmacien ---

    @GetMapping("/api/pharmacies/{pharmacy}/reservations/{code}")
    public PickupView lookup(Authentication auth, @PathVariable UUID pharmacy, @PathVariable String code) {
        return service.lookupPickup(user(auth), pharmacy, code);
    }

    @PostMapping("/api/pharmacies/{pharmacy}/reservations/{id}/pickup")
    public PickupConfirmation pickup(Authentication auth, @PathVariable UUID pharmacy, @PathVariable UUID id) {
        return service.validatePickup(user(auth), pharmacy, id);
    }
}
