package ma.ensa.pharma.prescription.domain;

/**
 * Ligne d'ordonnance lue par l'IA, après nettoyage. Le nom est vide si la ligne est illisible.
 *
 * @param id        numéro de la ligne (1, 2, 3...)
 * @param name      nom du médicament sans dosage ("DOLIPRANE"), vide si illisible
 * @param dosage    dosage tel qu'écrit ("1 g"), vide si absent
 * @param form      forme telle qu'écrite ("comprimé"), vide si absente
 * @param quantity  nombre de boîtes, null si non indiqué
 * @param uncertain true si l'IA a signalé un doute
 */
public record ReadLine(int id, String name, String dosage, String form, Integer quantity, boolean uncertain) {

    public boolean illegible() {
        return name == null || name.isBlank();
    }

    /** Texte affiché au patient : nom + dosage, tel que lu sur l'ordonnance. */
    public String readText() {
        if (illegible()) {
            return "(illisible)";
        }
        return dosage == null || dosage.isBlank() ? name : name + " " + dosage;
    }
}
