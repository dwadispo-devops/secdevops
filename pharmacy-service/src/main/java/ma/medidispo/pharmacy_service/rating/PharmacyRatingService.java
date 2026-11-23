package ma.medidispo.pharmacy_service.rating;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import static org.springframework.http.HttpStatus.*;
import static ma.medidispo.pharmacy_service.rating.PharmacyRatingDtos.*;

@Service
public class PharmacyRatingService {
    private final PharmacyRatingRepository repository;
    public PharmacyRatingService(PharmacyRatingRepository repository) { this.repository = repository; }

    private void eligible(UUID patient, UUID request, UUID reservation) {
        var eligibility = repository.eligibility(patient, request, reservation)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Réservation introuvable"));
        if (!eligibility.pickedUp())
            throw new ResponseStatusException(CONFLICT, "La notation est possible uniquement après un retrait confirmé.");
    }

    @Transactional
    public Rating create(UUID patient, UUID request, UUID reservation, int stars) {
        if (stars < 1 || stars > 5) throw new ResponseStatusException(BAD_REQUEST, "Note attendue entre 1 et 5");
        eligible(patient, request, reservation);
        return repository.insert(reservation, stars)
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Une note a déjà été enregistrée pour cette réservation."));
    }

    @Transactional
    public Rating get(UUID patient, UUID request, UUID reservation) {
        eligible(patient, request, reservation);
        return repository.find(reservation).orElse(null);
    }
}
