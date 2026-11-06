package ma.medidispo.pharmacy_service.pharmacy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pharmacies")
public class PharmacyController {

    private final PharmacyRepository repository;

    public PharmacyController(PharmacyRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/nearby")
    public List<NearbyPharmacy> nearby(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lng,
            @RequestParam(defaultValue = "2000") @Min(100) @Max(20000) int radius,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return repository.findNearby(lat, lng, radius, limit).stream()
                .map(NearbyPharmacy::from).toList();
    }

    // --- US03 : inscription d'une pharmacie par un pharmacien ---

    @PostMapping
    public ResponseEntity<PharmacyPublic> register(Authentication auth, @Valid @RequestBody RegisterPharmacyRequest request) {
        Pharmacy pharmacy = new Pharmacy(
                request.name(),
                request.address(),
                request.phone(),
                request.openingHours(),
                request.latitude(),
                request.longitude(),
                request.licenseNumber()
        );
        pharmacy.assignOwner((UUID) auth.getPrincipal());
        Pharmacy saved = repository.save(pharmacy);
        return ResponseEntity.status(HttpStatus.CREATED).body(PharmacyPublic.from(saved));
    }

    /** US03 : pharmacies du pharmacien connecté, avec leur statut (PENDING tant qu'elles ne sont pas validées). */
    @GetMapping("/me")
    public List<PharmacyPublic> mine(Authentication auth) {
        return repository.findByOwnerUserIdOrderByCreatedAtDesc((UUID) auth.getPrincipal()).stream()
                .map(PharmacyPublic::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PharmacyPublic> getById(@PathVariable UUID id) {
        return repository.findById(id)
                .map(PharmacyPublic::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public record RegisterPharmacyRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 300) String address,
            @Size(max = 30) String phone,
            @Size(max = 200) String openingHours,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @NotBlank @Size(max = 100) String licenseNumber
    ) {}
}
