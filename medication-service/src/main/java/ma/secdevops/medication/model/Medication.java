package ma.secdevops.medication.model;

import java.math.BigDecimal;

/**
 * Représentation d'une ligne du référentiel des médicaments CNOPS.
 * Utilisé en mémoire pendant l'import — pas d'entité JPA.
 *
 * @param code               Code EAN-13 (texte, 13 chiffres)
 * @param nom                Nom commercial
 * @param dci1               Dénomination Commune Internationale
 * @param dosage1            Dosage numérique (NULL si absent)
 * @param uniteDosage1       Unité du dosage
 * @param forme              Forme pharmaceutique
 * @param presentation       Conditionnement
 * @param ppv                Prix Public de Vente (NULL si 0 dans source)
 * @param ph                 Prix Hospitalier (NULL si 0 dans source)
 * @param prixBr             Prix de Revient Brut (NULL si 0 dans source)
 * @param princepsGenerique  'P' pour princeps, 'G' pour générique
 * @param tauxRemboursement  0 ou 70 (en entier)
 */
public record Medication(
        String code,
        String nom,
        String dci1,
        BigDecimal dosage1,
        String uniteDosage1,
        String forme,
        String presentation,
        BigDecimal ppv,
        BigDecimal ph,
        BigDecimal prixBr,
        String princepsGenerique,
        Integer tauxRemboursement
) {}
