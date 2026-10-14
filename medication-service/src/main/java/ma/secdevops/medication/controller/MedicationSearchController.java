package ma.secdevops.medication.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import ma.secdevops.medication.dto.ErrorResponse;
import ma.secdevops.medication.dto.MedicationSearchResponse;
import ma.secdevops.medication.exception.InvalidSearchParameterException;
import ma.secdevops.medication.service.MedicationSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Contrôleur REST pour la recherche et l'autocomplétion des médicaments.
 */
@RestController
@RequestMapping("/api/medications")
@Tag(name = "Medications", description = "Recherche et consultation du référentiel des médicaments")
public class MedicationSearchController {

    public static final int DEFAULT_LIMIT = 10;
    public static final int MIN_LIMIT = 1;
    public static final int MAX_LIMIT = 20;
    public static final int MIN_QUERY_LENGTH = 2;
    public static final int MAX_QUERY_LENGTH = 50;

    private final MedicationSearchService service;

    public MedicationSearchController(MedicationSearchService service) {
        this.service = service;
    }

    @GetMapping("/search")
    @Operation(
            summary = "Rechercher des médicaments par nom",
            description = "Recherche par autocomplétion insensible à la casse avec tri par pertinence : " +
                    "1. Préfixe (nom commençant par q), " +
                    "2. Sous-chaîne (nom contenant q), " +
                    "3. Trigrammes (similarité approximative pour tolérance aux fautes de frappe)."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Liste des médicaments trouvés (vide si aucun résultat)",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = MedicationSearchResponse.class))
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Paramètre q ou limit invalide",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<List<MedicationSearchResponse>> search(
            @Parameter(description = "Texte recherché (2 à 50 caractères)", required = true, example = "dolip")
            @RequestParam(name = "q", required = false) String q,

            @Parameter(description = "Nombre maximum de résultats (1 à 20, défaut 10)", example = "10")
            @RequestParam(name = "limit", required = false, defaultValue = "10") Integer limit) {

        if (q == null) {
            throw new InvalidSearchParameterException("Le paramètre de recherche 'q' est obligatoire.");
        }

        String trimmed = q.trim();
        if (trimmed.length() < MIN_QUERY_LENGTH) {
            throw new InvalidSearchParameterException(
                    String.format("Le paramètre de recherche 'q' doit contenir au moins %d caractères.", MIN_QUERY_LENGTH));
        }

        if (trimmed.length() > MAX_QUERY_LENGTH) {
            throw new InvalidSearchParameterException(
                    String.format("Le paramètre de recherche 'q' ne doit pas dépasser %d caractères.", MAX_QUERY_LENGTH));
        }

        int effectiveLimit = (limit != null) ? limit : DEFAULT_LIMIT;
        if (effectiveLimit < MIN_LIMIT || effectiveLimit > MAX_LIMIT) {
            throw new InvalidSearchParameterException(
                    String.format("Le paramètre 'limit' doit être compris entre %d et %d.", MIN_LIMIT, MAX_LIMIT));
        }

        List<MedicationSearchResponse> results = service.search(trimmed, effectiveLimit);
        return ResponseEntity.ok(results);
    }
}
