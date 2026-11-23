package ma.secdevops.medication.generics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import ma.secdevops.medication.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;

/**
 * US16 – Génériques moins chers ayant la même DCI.
 * Catalogue public, comme /search : aucune donnée patient n'est reçue ni enregistrée.
 */
@RestController
@RequestMapping("/api/medications")
@Tag(name = "Génériques (US16)", description = "Génériques moins chers de même DCI, dosage et forme")
public class GenericsController {

    public static final int DEFAULT_LIMIT = 10;
    public static final int MAX_LIMIT = 20;
    private static final String CODE_FORMAT = "[0-9]{13}";

    private final GenericsService service;

    public GenericsController(GenericsService service) {
        this.service = service;
    }

    @GetMapping("/{code}/generiques")
    @Operation(summary = "Génériques moins chers d'un médicament",
            description = "Génériques du catalogue CNOPS ayant la même DCI, le même dosage et la même forme, "
                    + "strictement moins chers (comparaison par unité de prise), du moins cher au plus cher.")
    @ApiResponse(responseCode = "200", description = "Liste (éventuellement vide, avec un message)")
    @ApiResponse(responseCode = "400", description = "Code ou limite invalide")
    @ApiResponse(responseCode = "404", description = "Code absent du catalogue")
    public ResponseEntity<GenericsResponse> generics(
            @Parameter(description = "Code EAN-13 du médicament", example = "6118000160182")
            @PathVariable String code,
            @Parameter(description = "Nombre maximum de génériques (1 à 20, défaut 10)", example = "10")
            @RequestParam(name = "limit", defaultValue = "10") int limit) {

        if (!code.matches(CODE_FORMAT)) {
            throw new InvalidGenericsRequest("Le code doit contenir 13 chiffres.");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidGenericsRequest("Le paramètre 'limit' doit être compris entre 1 et " + MAX_LIMIT + ".");
        }
        return ResponseEntity.ok(service.cheaperGenerics(code, limit));
    }

    @ExceptionHandler(InvalidGenericsRequest.class)
    public ResponseEntity<ErrorResponse> invalid(InvalidGenericsRequest ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> typeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Le paramètre 'limit' doit être un nombre entier.", request);
    }

    @ExceptionHandler(MedicationNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(MedicationNotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ErrorResponse(Instant.now(), status.value(),
                status.getReasonPhrase(), message, request.getRequestURI()));
    }

    public static class InvalidGenericsRequest extends RuntimeException {
        InvalidGenericsRequest(String message) {
            super(message);
        }
    }
}
