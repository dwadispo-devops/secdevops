package ma.medidispo.pharmacy_service.reservation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import static org.springframework.http.HttpStatus.*;
import static ma.medidispo.pharmacy_service.reservation.ReservationDtos.*;

@Service
public class ReservationService {
    /** RG03 : le code de retrait est valable 1 h. */
    static final Duration VALIDITE = Duration.ofHours(1);
    /** RG04 : au plus 3 réservations actives par patient. */
    static final int MAX_ACTIVES = 3;

    private final ReservationRepository repository;
    private final PickupCodeGenerator codes;

    public ReservationService(ReservationRepository repository, PickupCodeGenerator codes) {
        this.repository = repository; this.codes = codes;
    }

    @Transactional
    public Reservation reserve(UUID patient, UUID request, UUID pharmacy) {
        repository.lockPatient(patient);
        repository.expireDue();
        // Même 404 pour une demande absente ou appartenant à un autre patient.
        if (!repository.requestBelongs(patient, request))
            throw new ResponseStatusException(NOT_FOUND, "Demande introuvable");
        BigDecimal price = repository.availablePrice(request, pharmacy)
            .orElseThrow(() -> new ResponseStatusException(CONFLICT,
                "Cette pharmacie n'a pas confirmé la disponibilité de vos médicaments."));
        if (repository.activeForRequest(request))
            throw new ResponseStatusException(CONFLICT, "Une réservation est déjà active pour cette demande.");
        if (repository.activeCount(patient) >= MAX_ACTIVES)
            throw new ResponseStatusException(CONFLICT, "Vous avez déjà " + MAX_ACTIVES
                + " réservations actives : retirez-en une ou attendez son expiration.");
        UUID id = repository.insert(request, pharmacy, patient, codeLibre(pharmacy), price, (int) VALIDITE.toSeconds());
        return repository.find(id).orElseThrow();
    }

    private String codeLibre(UUID pharmacy) {
        for (int essai = 0; essai < 10; essai++) {
            String code = codes.next();
            if (!repository.codeTaken(pharmacy, code)) return code;
        }
        throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Impossible de générer un code de retrait, réessayez.");
    }

    public Reservation current(UUID patient, UUID request) {
        if (!repository.requestBelongs(patient, request))
            throw new ResponseStatusException(NOT_FOUND, "Demande introuvable");
        return repository.latest(patient, request)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Aucune réservation pour cette demande"));
    }

    /** RG03 : sans retrait dans l'heure, la réservation passe à EXPIREE. */
    @Scheduled(fixedDelayString = "${reservation.expiration.delai-ms:60000}")
    public void expirer() {
        repository.expireDue();
    }
}
