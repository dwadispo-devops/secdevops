package ma.ensa.pharma.prescription.storage;

/**
 * Cycle de vie d'une ordonnance enregistrée.
 * US06 crée l'ordonnance à l'état UPLOADED. US07 / US08 ajouteront leurs états (ex. READ, CONFIRMED).
 */
public enum PrescriptionStatus {
    UPLOADED
}
