package ma.ensa.pharma.prescription.ai;

import ma.ensa.pharma.prescription.config.ReadingProperties;
import ma.ensa.pharma.prescription.domain.ReadLine;
import ma.ensa.pharma.prescription.domain.VisionExtraction;
import ma.ensa.pharma.prescription.domain.VisionExtraction.ExtractedMedication;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validation de la sortie du modèle avant tout usage (sécurité de l'IA, SEC07) :
 * nettoyage des caractères de contrôle, des balises et des liens, longueurs maximales,
 * nombre maximal de lignes, quantités absurdes rejetées.
 * <p>
 * Les lignes illisibles (nom vide) sont conservées : elles apparaîtront en rouge
 * pour que le patient les saisisse à la main (EF10).
 */
@Component
public class OutputSanitizer {

    static final int MAX_FIELD_LENGTH = 60;
    static final int MAX_QUANTITY = 20;

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}]");
    private static final Pattern MARKUP = Pattern.compile("[<>{}`]");
    private static final Pattern URL = Pattern.compile("(?i)\\b(https?://|www\\.)\\S+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private final ReadingProperties properties;

    public OutputSanitizer(ReadingProperties properties) {
        this.properties = properties;
    }

    public List<ReadLine> sanitize(VisionExtraction extraction) {
        List<ReadLine> lines = new ArrayList<>();
        if (extraction == null || extraction.medications() == null) {
            return lines;
        }
        for (ExtractedMedication med : extraction.medications()) {
            if (lines.size() >= properties.maxLines()) {
                break;
            }
            if (med == null) {
                continue;
            }
            String name = clean(med.name());
            String dosage = clean(med.dosage());
            String form = clean(med.form());
            if (name.isEmpty() && dosage.isEmpty() && form.isEmpty() && !med.uncertain()) {
                continue; // ligne totalement vide : rien à afficher
            }
            lines.add(new ReadLine(lines.size() + 1, name, dosage, form,
                    validQuantity(med.quantity()), med.uncertain()));
        }
        return lines;
    }

    static String clean(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = CONTROL_CHARS.matcher(value).replaceAll(" ");
        cleaned = URL.matcher(cleaned).replaceAll(" ");
        cleaned = MARKUP.matcher(cleaned).replaceAll(" ");
        cleaned = SPACES.matcher(cleaned).replaceAll(" ").strip();
        if (cleaned.length() > MAX_FIELD_LENGTH) {
            cleaned = cleaned.substring(0, MAX_FIELD_LENGTH).strip();
        }
        return cleaned;
    }

    static Integer validQuantity(Integer quantity) {
        if (quantity == null || quantity < 1 || quantity > MAX_QUANTITY) {
            return null;
        }
        return quantity;
    }
}
