package ma.ensa.pharma.prescription.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Une ligne de l'ordonnance, rapprochée du catalogue (US07).
 * Format aligné sur l'interface {@code LigneOrdonnance} de l'écran de vérification (US08),
 * complété par la quantité prescrite.
 */
@Schema(description = "Ligne d'ordonnance lue et rapprochée du catalogue")
public record LigneOrdonnanceDto(
        @Schema(example = "1") int id,
        @Schema(description = "Texte lu par l'IA (nom + dosage)", example = "DOLIPRANE 1 g")
        String texteLu,
        @Schema(description = "Nombre de boîtes prescrites, null si non indiqué", example = "1")
        Integer quantite,
        @Schema(description = "Confiance entre 0 et 1 : ≥ 0,85 vert, 0,60 à 0,85 orange, < 0,60 rouge", example = "0.94")
        double score,
        @Schema(description = "Jusqu'à 3 produits du catalogue, du plus proche au moins proche")
        List<ProduitDto> suggestions) {
}
