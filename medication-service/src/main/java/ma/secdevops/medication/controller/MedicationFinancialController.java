package ma.secdevops.medication.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ma.secdevops.medication.dto.MedicationFinancialResponse;
import ma.secdevops.medication.repository.MedicationFinancialRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import ma.secdevops.medication.dto.ErrorResponse;
import java.time.Instant;
import java.util.List;

/** Catalogue public comme /search ; les codes inconnus sont absents de la réponse. */
@RestController
@RequestMapping("/api/medications")
public class MedicationFinancialController {
    private final MedicationFinancialRepository repository;
    public MedicationFinancialController(MedicationFinancialRepository repository) { this.repository = repository; }

    public record Lookup(@NotEmpty @Size(max = 100) List<@NotNull @Pattern(regexp = "[0-9]{13}") String> codes) {}

    @PostMapping("/lookup")
    public ResponseEntity<List<MedicationFinancialResponse>> lookup(@Valid @RequestBody Lookup request) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(repository.findByCodes(request.codes().stream().distinct().toList()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> invalid(Exception ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(Instant.now(), 400, "Bad Request",
                "Fournissez de 1 à 100 codes de 13 chiffres.", "/api/medications/lookup"));
    }
}
