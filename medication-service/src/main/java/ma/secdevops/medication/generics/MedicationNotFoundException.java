package ma.secdevops.medication.generics;

/** Aucun médicament du catalogue ne porte ce code. */
public class MedicationNotFoundException extends RuntimeException {
    public MedicationNotFoundException() {
        super("Médicament introuvable dans le catalogue.");
    }
}
