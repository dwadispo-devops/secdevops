package ma.secdevops.medication.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Format standard des réponses d'erreur de l'API.
 */
@Schema(description = "Structure d'erreur standard de l'API")
public record ErrorResponse(
        @Schema(description = "Horodatage de l'erreur (UTC)", example = "2026-10-06T20:50:00Z")
        Instant timestamp,

        @Schema(description = "Code statut HTTP", example = "400")
        int status,

        @Schema(description = "Type d'erreur HTTP", example = "Bad Request")
        String error,

        @Schema(description = "Description détaillée de l'erreur", example = "Le paramètre 'q' doit contenir entre 2 et 50 caractères.")
        String message,

        @Schema(description = "Chemin de la requête HTTP ayant généré l'erreur", example = "/api/medications/search")
        String path
) {}
