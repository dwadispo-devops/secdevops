package ma.ensa.pharma.prescription.api;

import ma.ensa.pharma.prescription.api.dto.ReadPrescriptionResponse;
import ma.ensa.pharma.prescription.security.PatientJwtService;
import ma.ensa.pharma.prescription.security.ReviewDelegationService;
import ma.ensa.pharma.prescription.service.StoredReadingService;
import ma.ensa.pharma.prescription.storage.PrescriptionStorageService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
public class StoredPrescriptionController {
    private final PatientJwtService patients;
    private final ReviewDelegationService delegation;
    private final StoredReadingService readings;
    private final PrescriptionStorageService storage;
    public StoredPrescriptionController(PatientJwtService patients, ReviewDelegationService delegation,
                                        StoredReadingService readings, PrescriptionStorageService storage) {
        this.patients=patients; this.delegation=delegation; this.readings=readings; this.storage=storage;
    }
    @PostMapping("/api/v1/prescriptions/{id}/read")
    public ResponseEntity<ReadPrescriptionResponse> read(@RequestHeader(value="Authorization",required=false) String auth,
                                                        @PathVariable UUID id,
                                                        @RequestParam(defaultValue="false") boolean consent) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(readings.read(patients.authenticatedPatientId(auth), id, consent));
    }
    @GetMapping("/internal/prescriptions/{id}")
    public ResponseEntity<StoredReadingService.View> view(@RequestHeader(value="Authorization",required=false) String auth,
                                                         @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(readings.view(delegation.patient(auth,id),id));
    }
    @GetMapping("/internal/prescriptions/{id}/image")
    public ResponseEntity<byte[]> image(@RequestHeader(value="Authorization",required=false) String auth, @PathVariable UUID id) {
        var image=storage.loadImage(delegation.patient(auth,id),id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(image.mediaType()))
            .header("X-Content-Type-Options","nosniff").body(image.data());
    }
}
