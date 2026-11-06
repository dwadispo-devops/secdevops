package ma.ensa.pharma.prescription.catalog;

import ma.ensa.pharma.prescription.domain.ReadLine;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calcule le score de confiance (0 à 1) entre une ligne lue par l'IA et un médicament du catalogue.
 * <pre>
 * score = 0,55 × similarité du nom  +  0,25 × correspondance du dosage  +  0,20 × correspondance de la forme
 * </pre>
 * <ul>
 *   <li><b>Nom</b> : similarité par trigrammes, comme l'extension PostgreSQL pg_trgm
 *       (tolère les fautes de lecture : "DOLIPRAN" ≈ "DOLIPRANE").</li>
 *   <li><b>Dosage</b> : comparé après conversion en mg ("1 g" = "1000 MG").
 *       1 si identique, 0 si différent, 0,3 si inconnu d'un côté (impossible à vérifier :
 *       la ligne ne peut alors pas être verte).</li>
 *   <li><b>Forme</b> : comparée après normalisation ("cp" = "comprimé" = "COMPRIME"), en regardant
 *       la forme ET la présentation du catalogue (ex. "POUDRE POUR SUSPENSION BUVABLE",
 *       "1 BOITE 16 SACHET" → sachet). 1 si une forme correspond, 0 sinon, 0,5 si inconnue.</li>
 * </ul>
 * Seuils d'affichage (cahier des charges §5.1) : ≥ 0,85 vert, 0,60 à 0,85 orange, &lt; 0,60 rouge.
 */
@Component
public class MatchScorer {

    static final double WEIGHT_NAME = 0.55;
    static final double WEIGHT_DOSAGE = 0.25;
    static final double WEIGHT_FORM = 0.20;
    static final double UNKNOWN_DOSAGE = 0.3;
    static final double UNKNOWN_FORM = 0.5;

    private static final Pattern DOSE = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(mg|g|µg|mcg|ug|ui|%|ml)?", Pattern.CASE_INSENSITIVE);

    /** Formes reconnues, dans l'ordre de priorité ; chaque forme a ses mots-clés (texte normalisé). */
    private static final String[][] FORMS = {
            {"comprime", "comprime", "comprimes", "cp", "cpr", "comp"},
            {"gelule", "gelule", "gelules"},
            {"sachet", "sachet", "sachets"},
            {"suppositoire", "suppositoire", "suppositoires", "suppo"},
            {"sirop", "sirop"},
            {"suspension", "suspension", "susp"},
            {"solution", "solution", "sol"},
            {"poudre", "poudre"},
            {"creme", "creme"},
            {"pommade", "pommade"},
            {"collyre", "collyre"},
            {"injectable", "injectable", "inj", "injection"},
            {"spray", "spray", "pulverisation", "nasal"},
    };

    public double score(ReadLine line, CatalogMedication candidate) {
        double name = nameSimilarity(line.name(), candidate.nom());
        double dosage = dosageScore(parseDose(line.dosage()), catalogDose(candidate));
        double form = formScore(line.form(), candidate.forme(), candidate.presentation());
        double score = WEIGHT_NAME * name + WEIGHT_DOSAGE * dosage + WEIGHT_FORM * form;
        return round(Math.max(0, Math.min(1, score)));
    }

    // ---------------------------------------------------------------- nom

    /** Similarité de trigrammes (indice de Jaccard), même principe que pg_trgm. */
    static double nameSimilarity(String a, String b) {
        Set<String> ta = trigrams(normalize(a));
        Set<String> tb = trigrams(normalize(b));
        if (ta.isEmpty() || tb.isEmpty()) {
            return 0;
        }
        Set<String> inter = new HashSet<>(ta);
        inter.retainAll(tb);
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        return (double) inter.size() / union.size();
    }

    private static Set<String> trigrams(String text) {
        Set<String> result = new HashSet<>();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            String padded = "  " + word + " ";
            for (int i = 0; i + 3 <= padded.length(); i++) {
                result.add(padded.substring(i, i + 3));
            }
        }
        return result;
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String n = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9%µ.,/ ]", " ").replaceAll("\\s+", " ").strip();
    }

    // ---------------------------------------------------------------- dosage

    static double dosageScore(String readDosage, BigDecimal catalogValue, String catalogUnit) {
        return dosageScore(parseDose(readDosage),
                catalogValue == null ? null : Dose.of(catalogValue.doubleValue(), catalogUnit));
    }

    static double dosageScore(Dose read, Dose catalog) {
        if (read == null || catalog == null) {
            return UNKNOWN_DOSAGE;
        }
        return read.sameAs(catalog) ? 1.0 : 0.0;
    }

    /**
     * Dosage du produit : {@code dosage1}/{@code uniteDosage1}, ou à défaut {@code dosageBrut}
     * s'il contient une unité explicite (ex. "1 G/125 MG"). Sans unité, on ne devine pas.
     */
    static Dose catalogDose(CatalogMedication candidate) {
        if (candidate.dosage1() != null) {
            return Dose.of(candidate.dosage1().doubleValue(), candidate.uniteDosage1());
        }
        Dose raw = parseDose(candidate.dosageBrut());
        return raw != null && raw.explicitUnit() ? raw : null;
    }

    /** Premier dosage lisible du texte : "1 g/125 mg" → 1000 mg. */
    static Dose parseDose(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher m = DOSE.matcher(text);
        if (!m.find()) {
            return null;
        }
        double value = Double.parseDouble(m.group(1).replace(',', '.'));
        Dose dose = Dose.of(value, m.group(2));
        return m.group(2) == null ? new Dose(dose.value(), dose.kind(), false) : dose;
    }

    record Dose(double value, String kind, boolean explicitUnit) {

        static Dose of(double value, String unit) {
            String u = unit == null ? "mg" : unit.toLowerCase(Locale.ROOT).strip();
            return switch (u) {
                case "g" -> new Dose(value * 1000, "mg", true);
                case "µg", "mcg", "ug" -> new Dose(value / 1000, "mg", true);
                case "mg", "" -> new Dose(value, "mg", true);
                default -> new Dose(value, u, true);
            };
        }

        boolean sameAs(Dose other) {
            if (!kind.equals(other.kind)) {
                return false;
            }
            double max = Math.max(Math.abs(value), Math.abs(other.value));
            return max == 0 || Math.abs(value - other.value) <= 0.01 * max;
        }
    }

    // ---------------------------------------------------------------- forme

    static double formScore(String readForm, String catalogForm) {
        return formScore(readForm, catalogForm, null);
    }

    /** Forme lue comparée à la forme ET à la présentation du produit du catalogue. */
    static double formScore(String readForm, String catalogForm, String catalogPresentation) {
        Set<String> read = canonicalForms(readForm);
        Set<String> catalog = new HashSet<>(canonicalForms(catalogForm));
        catalog.addAll(canonicalForms(catalogPresentation));
        if (read.isEmpty() || catalog.isEmpty()) {
            return UNKNOWN_FORM;
        }
        for (String form : read) {
            if (catalog.contains(form)) {
                return 1.0;
            }
        }
        return 0.0;
    }

    /** Toutes les formes reconnues dans un texte : "poudre en sachet" → {sachet, poudre}. */
    static Set<String> canonicalForms(String text) {
        Set<String> result = new HashSet<>();
        String n = normalize(text);
        if (n.isEmpty()) {
            return result;
        }
        // HashSet et non Set.of : une présentation répète souvent un mot ("1 BOITE 1 FLACON")
        Set<String> words = new HashSet<>(Arrays.asList(n.split("[ ,./]+")));
        for (String[] form : FORMS) {
            for (int i = 1; i < form.length; i++) {
                if (words.contains(form[i])) {
                    result.add(form[0]);
                    break;
                }
            }
        }
        return result;
    }

    static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
