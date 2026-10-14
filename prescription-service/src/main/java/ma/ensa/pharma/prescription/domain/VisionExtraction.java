package ma.ensa.pharma.prescription.domain;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Sortie structurée attendue du modèle vision (le schéma JSON est généré à partir de ce record
 * par Spring AI). Aucun champ ne permet au modèle de donner un conseil médical.
 */
public record VisionExtraction(
        @JsonPropertyDescription("true si l'image est bien une ordonnance médicale, false sinon")
        boolean prescriptionDetected,

        @JsonPropertyDescription("Liste des médicaments prescrits, dans l'ordre de l'ordonnance. Liste vide si aucun.")
        List<ExtractedMedication> medications) {

    public record ExtractedMedication(
            @JsonPropertyDescription("Texte exact de la ligne tel qu'il est écrit sur l'ordonnance")
            String rawText,

            @JsonPropertyDescription("Nom commercial ou DCI du médicament, tel qu'écrit. Vide si illisible")
            String name,

            @JsonPropertyDescription("Dosage tel qu'écrit, par exemple '1 g' ou '500 mg'. Vide si absent")
            String dosage,

            @JsonPropertyDescription("Forme galénique telle qu'écrite : comprimé, gélule, sirop, sachet... Vide si absente")
            String form,

            @JsonPropertyDescription("Posologie recopiée telle qu'écrite, par exemple '1 comprimé 3 fois par jour'. Vide si absente")
            String posology,

            @JsonPropertyDescription("Durée du traitement telle qu'écrite, par exemple '7 jours'. Vide si absente")
            String duration,

            @JsonPropertyDescription("Nombre de boîtes prescrites si indiqué, sinon null")
            Integer boxes,

            @JsonPropertyDescription("true si la ligne est difficile à lire ou incertaine")
            boolean uncertain) {
    }
}
