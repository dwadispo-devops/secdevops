package ma.ensa.pharma.prescription.domain;

/**
 * Statut d'une ligne lue par l'IA, avant rapprochement avec le catalogue.
 * <p>
 * Le code couleur final (vert / orange / rouge, RG01-EF09) dépendra du score de confiance du RAG
 * (Sprint 2). Dans la PoC, on distingue seulement ce que le modèle a pu lire.
 */
public enum LineStatus {
    /** Nom lu clairement par le modèle. */
    LU,
    /** Nom lu mais signalé comme incertain par le modèle. */
    INCERTAIN,
    /** Aucun nom exploitable : saisie manuelle ou nouvelle photo (EF10). */
    ILLISIBLE
}
