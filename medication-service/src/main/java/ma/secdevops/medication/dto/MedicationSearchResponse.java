package ma.secdevops.medication.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * DTO représentant un médicament retourné par l'autocomplétion / recherche.
 */
@Schema(description = "Médicament retourné par la recherche d'autocomplétion")
public record MedicationSearchResponse(
        @Schema(description = "Code-barres EAN-13", example = "6118000040958")
        String code,

        @Schema(description = "Nom commercial du médicament", example = "DOLIPRANE 1000 MG")
        String nom,

        @Schema(description = "Dénomination Commune Internationale", example = "PARACETAMOL")
        String dci1,

        @Schema(description = "Dosage numérique", example = "1000.000")
        BigDecimal dosage1,

        @Schema(description = "Unité du dosage", example = "MG")
        String uniteDosage1,

        @Schema(description = "Forme pharmaceutique", example = "COMPRIME")
        String forme,

        @Schema(description = "Présentation et conditionnement", example = "1 BOITE 8 COMPRIME")
        String presentation,

        @Schema(description = "Prix Public de Vente en MAD (null si hôpital uniquement)", example = "16.00")
        BigDecimal ppv,

        @Schema(description = "Taux de remboursement CNOPS en % (0 ou 70)", example = "70")
        Integer tauxRemboursement,

        @Schema(description = "P = Princeps, G = Générique", example = "P")
        String princepsGenerique,

        @Schema(description = "Dosage source sans interpretation", example = "600 / 5")
        String dosageBrut
) {}
