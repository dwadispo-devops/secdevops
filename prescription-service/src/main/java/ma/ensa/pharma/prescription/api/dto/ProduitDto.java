package ma.ensa.pharma.prescription.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Produit du catalogue CNOPS proposé au patient (même format que l'interface {@code Produit} du front). */
@Schema(description = "Produit du catalogue CNOPS")
public record ProduitDto(
        @Schema(description = "Code CNOPS du produit", example = "6118000040279")
        String id,
        @Schema(description = "Libellé du produit", example = "DOLIPRANE 1000 MG, COMPRIME, 1 BOITE 8 COMPRIME")
        String nom) {
}
