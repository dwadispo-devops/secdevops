package ma.ensa.pharma.prescription.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ma.ensa.pharma.prescription.domain.LineStatus;

@Schema(description = "Une ligne de médicament lue sur l'ordonnance (avant rapprochement avec le catalogue)")
public record MedicationLineDto(
        @Schema(example = "1") int index,
        @Schema(description = "Texte de la ligne tel qu'écrit", example = "Doliprane 1g cp - 1 cp 3x/jour pendant 5 jours")
        String rawText,
        @Schema(example = "Doliprane") String name,
        @Schema(example = "1 g") String dosage,
        @Schema(example = "comprimé") String form,
        @Schema(example = "1 comprimé 3 fois par jour") String posology,
        @Schema(example = "5 jours") String duration,
        @Schema(description = "Nombre de boîtes, null si non indiqué", example = "2") Integer boxes,
        LineStatus status) {
}
