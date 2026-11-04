package ma.ensa.pharma.prescription.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Médicament du référentiel CNOPS, tel que renvoyé par medication-service
 * ({@code GET /api/medications/search}). Seuls les champs utiles au rapprochement sont lus.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogMedication(
        String code,
        String nom,
        String dci1,
        BigDecimal dosage1,
        String uniteDosage1,
        String forme,
        String presentation,
        String dosageBrut) {

    /** Libellé lisible pour le patient : "DOLIPRANE 1000 MG, COMPRIME, 1 BOITE 8 COMPRIME". */
    public String label() {
        StringBuilder sb = new StringBuilder(nom == null ? "" : nom.strip());
        if (dosage1 != null) {
            sb.append(' ').append(dosage1.stripTrailingZeros().toPlainString());
            if (uniteDosage1 != null && !uniteDosage1.isBlank()) {
                sb.append(' ').append(uniteDosage1.strip());
            }
        } else if (dosageBrut != null && !dosageBrut.isBlank()) {
            sb.append(' ').append(dosageBrut.strip());
        }
        if (forme != null && !forme.isBlank()) {
            sb.append(", ").append(forme.strip());
        }
        if (presentation != null && !presentation.isBlank()) {
            sb.append(", ").append(presentation.strip());
        }
        return sb.toString();
    }
}
