package ma.medidispo.pharmacy_service.review;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;
import static org.springframework.http.HttpStatus.*;
import static ma.medidispo.pharmacy_service.review.ReviewDtos.*;

@Service
public class ReviewService {
    private final ReviewRepository repository;
    private final PrescriptionClient prescriptions;
    public ReviewService(ReviewRepository repository, PrescriptionClient prescriptions) {
        this.repository=repository; this.prescriptions=prescriptions;
    }
    public List<Request> inbox(UUID owner) { return repository.inbox(owner); }
    private Request assigned(UUID owner, UUID pharmacy, UUID request) {
        // Same 404 for absent and foreign requests: do not disclose medical metadata.
        return repository.assigned(owner, pharmacy, request)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Demande introuvable"));
    }
    public Detail detail(UUID owner, UUID pharmacy, UUID request) {
        return new Detail(assigned(owner, pharmacy, request), repository.medications(request));
    }
    @Transactional
    public Request respond(UUID owner, UUID pharmacy, UUID request, Response response) {
        assigned(owner, pharmacy, request);
        repository.lockRequest(request);
        if ((response.availability()==Availability.AVAILABLE && response.price()==null)
            || (response.availability()==Availability.UNAVAILABLE && response.price()!=null)) {
            throw new ResponseStatusException(BAD_REQUEST, "Prix obligatoire si disponible, absent si indisponible");
        }
        if (repository.respond(owner, pharmacy, request, response)!=1)
            throw new ResponseStatusException(CONFLICT, "Une réponse a déjà été enregistrée");
        return assigned(owner, pharmacy, request);
    }
    public Prescription prescription(UUID owner, UUID pharmacy, UUID request) {
        var r = assigned(owner, pharmacy, request);
        if (r.prescriptionId()==null) return new Prescription(false, null, null, List.of());
        return prescriptions.details(repository.patient(request).orElseThrow(), r.prescriptionId());
    }
    public ResponseEntity<byte[]> image(UUID owner, UUID pharmacy, UUID request) {
        var r = assigned(owner, pharmacy, request);
        if (r.prescriptionId()==null) throw new ResponseStatusException(NOT_FOUND, "Aucune ordonnance associée");
        return prescriptions.image(repository.patient(request).orElseThrow(), r.prescriptionId());
    }
    @Transactional
    public void link(UUID patient, UUID request, UUID prescription) {
        requirePatient(patient, request);
        repository.lockRequest(request);
        // Upstream verifies the prescription belongs to this patient before any write.
        prescriptions.details(patient, prescription);
        if (repository.link(patient, request, prescription)!=1)
            throw new ResponseStatusException(CONFLICT, "Ordonnance déjà associée ou demande déjà traitée");
    }
    private void requirePatient(UUID patient, UUID request) {
        if (!repository.patient(request).filter(patient::equals).isPresent())
            throw new ResponseStatusException(NOT_FOUND, "Demande introuvable");
    }
    public List<Request> responses(UUID patient, UUID request) {
        requirePatient(patient, request);
        return repository.responses(patient, request);
    }
}
