package ma.ensa.pharma.prescription.ai;

import ma.ensa.pharma.prescription.api.dto.MedicationLineDto;
import ma.ensa.pharma.prescription.config.ReadingProperties;
import ma.ensa.pharma.prescription.domain.LineStatus;
import ma.ensa.pharma.prescription.domain.VisionExtraction;
import ma.ensa.pharma.prescription.domain.VisionExtraction.ExtractedMedication;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validation de la sortie du modèle avant de la renvoyer au client (sécurité de l'IA, SEC07) :
 * nettoyage des caractères de contrôle et des balises, longueur maximale des champs,
 * nombre maximal de lignes, rejet des lignes vides et des quantités absurdes.
 * <p>
 * La garantie "aucun produit inventé" (RG02) sera apportée au Sprint 2 par le rapprochement
 * avec le catalogue CNOPS : seuls les produits existants seront proposés au patient.
 */
@Component
public class OutputSanitizer {

    static final int MAX_FIELD_LENGTH = 120;
    static final int MAX_RAW_LENGTH = 250;
    static final int MAX_BOXES = 20;

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");
    private static final Pattern MARKUP = Pattern.compile("[<>{}`]");
    private static final Pattern URL = Pattern.compile("(?i)\\b(https?://|www\\.)\\S+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private final ReadingProperties properties;

    public OutputSanitizer(ReadingProperties properties) {
        this.properties = properties;
    }

    public List<MedicationLineDto> sanitize(VisionExtraction extraction) {
        List<MedicationLineDto> lines = new ArrayList<>();
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
            String raw = clean(med.rawText(), MAX_RAW_LENGTH);
            String name = clean(med.name(), MAX_FIELD_LENGTH);
            if (raw.isEmpty() && name.isEmpty()) {
                continue;
            }
            LineStatus status = name.isEmpty()
                    ? LineStatus.ILLISIBLE
                    : (med.uncertain() ? LineStatus.INCERTAIN : LineStatus.LU);
            lines.add(new MedicationLineDto(
                    lines.size() + 1,
                    raw,
                    name,
                    clean(med.dosage(), MAX_FIELD_LENGTH),
                    clean(med.form(), MAX_FIELD_LENGTH),
                    clean(med.posology(), MAX_RAW_LENGTH),
                    clean(med.duration(), MAX_FIELD_LENGTH),
                    validBoxes(med.boxes()),
                    status));
        }
        return lines;
    }

    static String clean(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String cleaned = CONTROL_CHARS.matcher(value).replaceAll(" ");
        cleaned = URL.matcher(cleaned).replaceAll(" ");
        cleaned = MARKUP.matcher(cleaned).replaceAll(" ");
        cleaned = SPACES.matcher(cleaned).replaceAll(" ").strip();
        if (cleaned.length() > maxLength) {
            cleaned = cleaned.substring(0, maxLength).strip();
        }
        return cleaned;
    }

    private static Integer validBoxes(Integer boxes) {
        if (boxes == null || boxes < 1 || boxes > MAX_BOXES) {
            return null;
        }
        return boxes;
    }
}
