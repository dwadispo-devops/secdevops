package ma.medidispo.pharmacy_service.reservation;

import jakarta.validation.Valid;
import ma.medidispo.pharmacy_service.review.PrescriptionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(ReservationController.class);

    private final ReservationService service;
    private final ReservationRepository repository;
    private final PrescriptionClient prescriptionClient;

    public ReservationController(ReservationService service,
                                 ReservationRepository repository,
                                 PrescriptionClient prescriptionClient) {
        this.service = service;
        this.repository = repository;
        this.prescriptionClient = prescriptionClient;
    }

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

    @PostMapping("/api/availability-requests/{request}/reservations/{id}/cancel")
    public Reservation cancel(Authentication auth, @PathVariable UUID request, @PathVariable UUID id) {
        return service.cancel(user(auth), request, id);
    }

    // --- US13 : Pharmacien ---

    @GetMapping("/api/pharmacies/{pharmacy}/reservations/{code}")
    public PickupView lookup(Authentication auth, @PathVariable UUID pharmacy, @PathVariable String code) {
        return service.lookupPickup(user(auth), pharmacy, code);
    }

    @PostMapping("/api/pharmacies/{pharmacy}/reservations/{id}/pickup")
    public PickupConfirmation pickup(Authentication auth, @PathVariable UUID pharmacy, @PathVariable UUID id) {
        var validated = service.validatePickup(user(auth), pharmacy, id);

        boolean imageSupprimee = true;
        var prescriptionId = repository.findPrescriptionId(validated.requestId());
        if (prescriptionId.isPresent()) {
            try {
                prescriptionClient.purgeImage(validated.patientId(), prescriptionId.get());
            } catch (Exception ex) {
                log.warn("Échec de la purge de l'image pour la réservation {} : {}", id, ex.getClass().getSimpleName());
                imageSupprimee = false;
            }
        }

        return new PickupConfirmation(validated.id(), "RETIREE", validated.pickedUpAt(), imageSupprimee);
    }
}
