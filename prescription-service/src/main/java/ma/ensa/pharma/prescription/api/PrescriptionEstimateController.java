package ma.ensa.pharma.prescription.api;

import jakarta.validation.Valid;
import ma.ensa.pharma.prescription.api.dto.PrescriptionEstimateDtos.*;
import ma.ensa.pharma.prescription.security.PatientJwtService;
import ma.ensa.pharma.prescription.service.PrescriptionEstimateService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class PrescriptionEstimateController {
    private final PatientJwtService patients;
    private final PrescriptionEstimateService estimates;
    public PrescriptionEstimateController(PatientJwtService patients, PrescriptionEstimateService estimates) {
        this.patients = patients; this.estimates = estimates;
    }
    @PostMapping("/api/v1/prescriptions/estimate")
    public ResponseEntity<Estimate> estimate(@RequestHeader(value = "Authorization", required = false) String auth,
                                              @Valid @RequestBody Request request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(estimates.estimate(patients.authenticatedPatientId(auth), request));
    }
}
