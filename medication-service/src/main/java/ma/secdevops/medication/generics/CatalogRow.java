package ma.secdevops.medication.generics;

import java.math.BigDecimal;

/**
 * US16 – Ligne du catalogue CNOPS utile à la comparaison des génériques.
 *
 * @param dosageBrut  dosage tel qu'écrit dans la source (ex. "1 / 125" pour une association)
 * @param dosage1     dosage numérique (NULL pour les associations)
 * @param uniteDosage1 unité(s) du dosage (ex. "MG", "G / MG")
 */
public record CatalogRow(
        String code,
        String nom,
        String dci1,
        String dosageBrut,
        BigDecimal dosage1,
        String uniteDosage1,
        String forme,
        String presentation,
        BigDecimal ppv,
        Integer tauxRemboursement,
        String princepsGenerique
) {
    public boolean isGeneric() {
        return "G".equals(princepsGenerique);
    }

    /** Dosage lisible : "1 / 125 G / MG", "500 MG"... */
    public String dosageLabel() {
        String value = dosageBrut != null ? dosageBrut
                : dosage1 != null ? dosage1.stripTrailingZeros().toPlainString() : null;
        if (value == null) {
            return null;
        }
        return uniteDosage1 == null ? value : value + " " + uniteDosage1;
    }
}
