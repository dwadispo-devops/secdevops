package ma.ensa.pharma.prescription.api;

import ma.ensa.pharma.prescription.security.PatientJwtService;
import ma.ensa.pharma.prescription.service.FamilyProfileService;
import ma.ensa.pharma.prescription.storage.FamilyProfile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/prescriptions/profiles")
public class FamilyProfileController {
    private final PatientJwtService patients;
    private final FamilyProfileService profiles;

    public FamilyProfileController(PatientJwtService patients, FamilyProfileService profiles) {
        this.patients = patients;
        this.profiles = profiles;
    }

    @GetMapping
    public ResponseEntity<List<ProfileResponse>> list(@RequestHeader(value = "Authorization", required = false) String auth) {
        UUID patient = patients.authenticatedPatientId(auth);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(profiles.list(patient).stream().map(ProfileResponse::from).toList());
    }

    @PostMapping
    public ResponseEntity<ProfileResponse> create(@RequestHeader(value = "Authorization", required = false) String auth,
                                                   @RequestBody ProfileRequest request) {
        ProfileResponse created = ProfileResponse.from(profiles.create(patients.authenticatedPatientId(auth), request.label()));
        return ResponseEntity.created(URI.create("/api/v1/prescriptions/profiles/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ProfileResponse> rename(@RequestHeader(value = "Authorization", required = false) String auth,
                                                   @PathVariable UUID id, @RequestBody ProfileRequest request) {
        return ResponseEntity.ok(ProfileResponse.from(
                profiles.rename(patients.authenticatedPatientId(auth), id, request.label())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestHeader(value = "Authorization", required = false) String auth,
                                       @PathVariable UUID id) {
        profiles.hide(patients.authenticatedPatientId(auth), id);
        return ResponseEntity.noContent().build();
    }

    public record ProfileRequest(String label) {}
    public record ProfileResponse(UUID id, String label, Instant createdAt) {
        static ProfileResponse from(FamilyProfile profile) {
            return new ProfileResponse(profile.getId(), profile.getLabel(), profile.getCreatedAt());
        }
    }
}
