package ma.medidispo.pharmacy_service.reservation;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import static ma.medidispo.pharmacy_service.reservation.ReservationDtos.*;

/** US12 – En tant que patient, je veux réserver et obtenir un code de retrait valable 1 h. */
@RestController
public class ReservationController {
    private final ReservationService service;
    public ReservationController(ReservationService service) { this.service = service; }
    private UUID user(Authentication auth) { return (UUID) auth.getPrincipal(); }

    @PostMapping("/api/availability-requests/{request}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public Reservation reserve(Authentication auth, @PathVariable UUID request, @Valid @RequestBody CreateReservation body) {
        return service.reserve(user(auth), request, body.pharmacyId());
    }

    @GetMapping("/api/availability-requests/{request}/reservation")
    public Reservation current(Authentication auth, @PathVariable UUID request) {
        return service.current(user(auth), request);
    }
}
