package ma.secdevops.medication.generics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * US16 – Règles de comparaison entre un médicament et ses génériques.
 *
 * Un générique n'est proposé que s'il a la même DCI, le même dosage et la même forme : sinon il ne
 * remplace pas le médicament prescrit. Les prix sont comparés par unité (comprimé, gélule, sachet...)
 * pour ne pas comparer une boîte de 12 avec une boîte de 24.
 */
final class GenericsRules {

    private GenericsRules() {
    }

    /** Unités de prise que l'on peut compter dans la présentation ("1 BOITE 16 SACHET" → 16). */
    private static final Set<String> COUNTABLE = Set.of(
            "COMPRIME", "COMPRIMES", "GELULE", "GELULES", "SACHET", "SACHETS", "SUPPOSITOIRE", "SUPPOSITOIRES",
            "CAPSULE", "CAPSULES", "AMPOULE", "AMPOULES", "OVULE", "OVULES", "DOSE", "DOSES", "UNIDOSE",
            "UNIDOSES", "PATCH", "PATCHS", "STYLO", "STYLOS", "SERINGUE", "SERINGUES", "PASTILLE", "PASTILLES",
            "DRAGEE", "DRAGEES", "LYOPHILISAT", "LYOPHILISATS");

    private static final Pattern QUANTITY = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*([A-Z]+)");

    /** Majuscules, sans accents, espaces simplifiés. */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String noAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /** Même DCI : "Amoxicilline / acide clavulanique" = "AMOXICILLINE / ACIDE CLAVULANIQUE". */
    static String dciKey(String dci) {
        return normalize(dci).replaceAll("\\s*/\\s*", " / ");
    }

    /**
     * Forme comparable : la faute de frappe "SUSPENTION" du fichier CNOPS est corrigée et la mention
     * "SECABLE" est ignorée (un comprimé sécable reste un comprimé). Les autres différences
     * (effervescent, gastro-résistant, injectable...) changent la forme et empêchent la substitution.
     */
    static String formKey(String forme) {
        String f = normalize(forme).replace("SUSPENTION", "SUSPENSION");
        f = f.replaceAll("\\bSECABLES?\\b", "");
        return f.replaceAll("\\s+", " ").trim();
    }

    /**
     * Dosage converti en milligrammes quand c'est possible : "1 G" = "1000 MG", "1 / 125 G / MG" = [1000 MG, 125 MG].
     *
     * @return null si le dosage est absent ou illisible (aucune comparaison possible)
     */
    static List<String> doseKey(CatalogRow row) {
        String values = row.dosageBrut() != null ? row.dosageBrut()
                : row.dosage1() != null ? row.dosage1().toPlainString() : null;
        if (values == null || row.uniteDosage1() == null) {
            return null;
        }
        String[] parts = values.split("/");
        String[] units = normalize(row.uniteDosage1()).split("/");
        if (parts.length != units.length) {
            return null;
        }
        List<String> key = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            BigDecimal value;
            try {
                value = new BigDecimal(parts[i].trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                return null;
            }
            String unit = units[i].trim();
            switch (unit) {
                case "G" -> { value = value.multiply(BigDecimal.valueOf(1000)); unit = "MG"; }
                case "MCG", "UG", "µG" -> { value = value.divide(BigDecimal.valueOf(1000)); unit = "MG"; }
                default -> { }
            }
            key.add(value.stripTrailingZeros().toPlainString() + " " + unit);
        }
        return key;
    }

    /** Nombre d'unités de prise dans la boîte, ou null (flacon de sirop, tube de crème...). */
    static Integer unitCount(String presentation) {
        Matcher m = QUANTITY.matcher(normalize(presentation));
        String number = null;
        String unit = null;
        while (m.find()) {
            number = m.group(1);
            unit = m.group(2);
        }
        if (number == null || !COUNTABLE.contains(unit) || number.contains(".") || number.contains(",")) {
            return null;
        }
        int count = Integer.parseInt(number);
        return count > 0 ? count : null;
    }

    static BigDecimal unitPrice(BigDecimal ppv, int count) {
        return ppv.divide(BigDecimal.valueOf(count), 6, RoundingMode.HALF_UP);
    }

    /** Économie en % arrondie à l'entier inférieur, pour ne jamais l'exagérer. */
    static int savingPercent(BigDecimal reference, BigDecimal generic) {
        return BigDecimal.ONE.subtract(generic.divide(reference, 6, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.DOWN).intValue();
    }
}
