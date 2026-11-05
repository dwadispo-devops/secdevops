package ma.ensa.pharma.prescription.domain;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Sortie structurée attendue du modèle vision (le schéma JSON est généré à partir de ce record
 * par Spring AI).
 * <p>
 * Le nom, le dosage et la forme sont demandés séparément : ils servent à retrouver le bon produit
 * dans le catalogue CNOPS (US07). La réponse finale ne garde que le texte lu, la quantité et
 * les produits du catalogue.
 */
public record VisionExtraction(
        @JsonPropertyDescription("true si l'image est bien une ordonnance médicale, false sinon")
        boolean prescriptionDetected,

        @JsonPropertyDescription("Liste des médicaments prescrits, dans l'ordre de l'ordonnance. Liste vide si aucun.")
        List<ExtractedMedication> medications) {

    public record ExtractedMedication(
            @JsonPropertyDescription("Nom commercial (ou DCI) du médicament SEUL, sans dosage ni forme, "
                    + "tel qu'écrit, par exemple 'DOLIPRANE' ou 'AUGMENTIN'. Vide si illisible")
            String name,

            @JsonPropertyDescription("Dosage tel qu'écrit, par exemple '1 g', '500 mg' ou '1 g/125 mg'. Vide si absent")
            String dosage,

            @JsonPropertyDescription("Forme telle qu'écrite : comprimé, gélule, sachet, sirop, suppositoire... Vide si absente")
            String form,

            @JsonPropertyDescription("Nombre de boîtes prescrites (mention 'QSP' ou 'N boîtes'). null si non indiqué")
            Integer quantity,

            @JsonPropertyDescription("true si la ligne est difficile à lire ou incertaine")
            boolean uncertain) {
    }
}
