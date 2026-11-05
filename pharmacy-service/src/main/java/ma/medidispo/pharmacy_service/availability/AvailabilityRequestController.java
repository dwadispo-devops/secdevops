package ma.medidispo.pharmacy_service.availability;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/availability-requests")
public class AvailabilityRequestController {

    private final AvailabilityRequestRepository repository;
    private final AvailabilityRequestItemRepository itemRepository;
    private final AvailabilityJwtService jwtService;
    private final AvailabilityPharmacySearchService pharmacySearchService;
    private final AvailabilityRequestPharmacyRepository pharmacyAssociationRepository;

    public AvailabilityRequestController(
            AvailabilityRequestRepository repository,
            AvailabilityRequestItemRepository itemRepository,
            AvailabilityJwtService jwtService,
            AvailabilityPharmacySearchService pharmacySearchService,
            AvailabilityRequestPharmacyRepository pharmacyAssociationRepository) {
        this.repository = repository;
        this.itemRepository = itemRepository;
        this.jwtService = jwtService;
        this.pharmacySearchService = pharmacySearchService;
        this.pharmacyAssociationRepository = pharmacyAssociationRepository;
    }

    @Transactional
    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody CreateRequest body) {

        UUID patientId;

        try {
            patientId = jwtService.authenticatedPatientId(authorization);
        } catch (SecurityException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token patient invalide");
        } catch (io.jsonwebtoken.JwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token JWT invalide ou expire");
        }

        if (body == null || body.latitude() == null || body.longitude() == null
                || body.radiusM() == null
                || !Double.isFinite(body.latitude()) || !Double.isFinite(body.longitude())
                || body.latitude() < -90 || body.latitude() > 90
                || body.longitude() < -180 || body.longitude() > 180
                || body.radiusM() < 100 || body.radiusM() > 20000
                || body.medications() == null || body.medications().isEmpty()
                || body.medications().stream().anyMatch(m -> m == null || m.name() == null || m.name().isBlank() || m.name().length() > 200 || m.quantity() == null || m.quantity() <= 0)) {
            return ResponseEntity.badRequest().body("Position ou rayon invalide");
        }

        AvailabilityRequest request = repository.save(
                new AvailabilityRequest(patientId, body.latitude(), body.longitude(), body.radiusM()));

        for (Medication medication : body.medications()) {
            itemRepository.save(new AvailabilityRequestItem(request.getId(), medication.name(), medication.quantity()));
        }

        var nearbyPharmacies = pharmacySearchService.findApprovedNearby(
                body.latitude(), body.longitude(), body.radiusM());

        for (var pharmacy : nearbyPharmacies) {
            pharmacyAssociationRepository.save(
                    new AvailabilityRequestPharmacy(request.getId(), pharmacy.getId()));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(request);
    }

    public record CreateRequest(Double latitude, Double longitude, Integer radiusM, List<Medication> medications) {}
    public record Medication(String name, Integer quantity) {}
}
