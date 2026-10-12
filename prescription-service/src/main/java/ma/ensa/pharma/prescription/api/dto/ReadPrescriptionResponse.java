package ma.ensa.pharma.prescription.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Résultat de la lecture d'une ordonnance par le modèle vision")
public record ReadPrescriptionResponse(
        @Schema(description = "Identifiant de la lecture (aucune donnée n'est conservée dans la PoC)")
        UUID readingId,
        @Schema(description = "false si l'image ne ressemble pas à une ordonnance")
        boolean prescriptionDetected,
        List<MedicationLineDto> lines,
        @Schema(description = "Fournisseur du modèle vision utilisé", example = "google-genai")
        String provider,
        @Schema(description = "Durée totale de traitement en millisecondes (objectif ENF02 : < 15 000)")
        long durationMs,
        @Schema(description = "Avertissements destinés à l'écran de vérification")
        List<String> warnings) {
}
