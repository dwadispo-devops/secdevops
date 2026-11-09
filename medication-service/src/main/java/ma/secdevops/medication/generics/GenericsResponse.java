package ma.secdevops.medication.generics;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/** US16 – Réponse de GET /api/medications/{code}/generiques. */
@Schema(description = "Génériques moins chers ayant la même DCI, le même dosage et la même forme")
public record GenericsResponse(
        @Schema(description = "Médicament de départ")
        Product reference,

        @Schema(description = "Génériques moins chers, du moins cher au plus cher (prix par unité)")
        List<CheaperGeneric> generiques,

        @Schema(description = "Explication affichable au patient, ou null quand des génériques sont trouvés")
        String message,

        @Schema(example = "Catalogue CNOPS 2014 – prix publics indicatifs")
        String source
) {

    public static final String SOURCE = "Catalogue CNOPS 2014 – prix publics indicatifs";

    @Schema(description = "Médicament de départ")
    public record Product(
            @Schema(example = "6118000160182") String code,
            @Schema(example = "AUGMENTIN") String nom,
            @Schema(example = "AMOXICILLINE / ACIDE CLAVULANIQUE") String dci,
            @Schema(example = "1 / 125 G / MG") String dosage,
            @Schema(example = "POUDRE POUR SUSPENSION BUVABLE") String forme,
            @Schema(example = "1 BOITE 16 SACHET") String presentation,
            @Schema(description = "Prix public de vente (MAD)", example = "168.20") BigDecimal ppv,
            @Schema(description = "Prix par unité de prise (MAD), null si non calculable", example = "10.51") BigDecimal prixUnitaire,
            @Schema(example = "70") Integer tauxRemboursement,
            @Schema(description = "P = princeps, G = générique", example = "P") String princepsGenerique
    ) {
    }

    @Schema(description = "Générique moins cher")
    public record CheaperGeneric(
            @Schema(example = "6118000041177") String code,
            @Schema(example = "ZAMOX") String nom,
            @Schema(example = "1 / 125 G / MG") String dosage,
            @Schema(example = "POUDRE POUR SUSPENSION BUVABLE") String forme,
            @Schema(example = "1 BOITE 24 SACHET") String presentation,
            @Schema(description = "Prix public de vente de la boîte (MAD)", example = "167.00") BigDecimal ppv,
            @Schema(description = "Prix par unité de prise (MAD)", example = "6.96") BigDecimal prixUnitaire,
            @Schema(description = "Économie par rapport au médicament de départ, en %", example = "33") int economiePourcent,
            @Schema(description = "PAR_UNITE (comprimé, sachet...) ou PAR_BOITE (même conditionnement)", example = "PAR_UNITE")
            Comparison comparaison,
            @Schema(example = "70") Integer tauxRemboursement,
            @Schema(description = "Vrai si ce générique n'est pas remboursé alors que le médicament de départ l'est")
            boolean moinsRembourse
    ) {
    }

    public enum Comparison { PAR_UNITE, PAR_BOITE }
}
