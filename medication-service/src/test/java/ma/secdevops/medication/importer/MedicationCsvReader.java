package ma.secdevops.medication.importer;

import ma.secdevops.medication.model.Medication;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lecteur CSV pour les tests d'intégration.
 * <p>
 * Applique les mêmes règles de nettoyage que {@link MedicationExcelReader} :
 * - PPV / PH / PRIX_BR = 0 → NULL
 * - DOSAGE1 virgule → point
 * - CODE invalide → ligne ignorée
 * - NOM absent → ligne ignorée
 * - Taux "70%" → 70
 * - Doublon de CODE → géré par l'upsert en base
 * </p>
 * <p>
 * Note : Cette classe est dans src/test uniquement, elle n'est pas embarquée
 * dans l'artefact de production.
 * </p>
 */
public class MedicationCsvReader {

    private static final Logger log = LoggerFactory.getLogger(MedicationCsvReader.class);

    public List<Medication> read(InputStream is, ImportResult result) throws IOException {
        List<Medication> medications = new ArrayList<>();

        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build()
                .parse(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            for (CSVRecord record : parser) {
                result.incrementRead();
                try {
                    Medication med = parseRecord(record, (int) record.getRecordNumber());
                    if (med != null) {
                        medications.add(med);
                    } else {
                        result.addIgnored("Ligne " + record.getRecordNumber() + " : code ou nom invalide");
                    }
                } catch (Exception e) {
                    result.addIgnored("Ligne " + record.getRecordNumber() + " : " + e.getMessage());
                    log.warn("Ligne {} ignorée : {}", record.getRecordNumber(), e.getMessage());
                }
            }
        }

        return medications;
    }

    private Medication parseRecord(CSVRecord r, int lineNum) {
        String code = r.get("CODE").trim();
        if (code.isBlank() || !code.matches("\\d{13}")) {
            log.warn("Ligne {} ignorée : CODE invalide '{}'", lineNum, code);
            return null;
        }

        String nom = r.get("NOM").trim();
        if (nom.isBlank()) {
            log.warn("Ligne {} ignorée : NOM absent (code={})", lineNum, code);
            return null;
        }

        String dci1 = nullIfBlank(r.get("DCI1"));
        BigDecimal dosage1 = parseDecimal(r.get("DOSAGE1"));
        String unite = nullIfBlank(r.get("UNITE_DOSAGE1"));
        String forme = nullIfBlank(r.get("FORME"));
        String presentation = nullIfBlank(r.get("PRESENTATION"));
        BigDecimal ppv = parsePrixZeroToNull(r.get("PPV"));
        BigDecimal ph = parsePrixZeroToNull(r.get("PH"));
        BigDecimal prixBr = parsePrixZeroToNull(r.get("PRIX_BR"));
        String pg = nullIfBlank(r.get("PRINCEPS_GENERIQUE"));
        Integer taux = parseTaux(r.get("TAUX_REMBOURSEMENT"));

        return new Medication(code, nom, dci1, dosage1, unite, forme, presentation,
                ppv, ph, prixBr, pg, taux, r.get("DOSAGE1").isBlank() ? null : r.get("DOSAGE1"));
    }

    private BigDecimal parsePrixZeroToNull(String s) {
        if (s == null || s.isBlank() || s.equals("-")) return null;
        // Supprimer espaces normaux et insécables (séparateurs de milliers)
        s = s.replaceAll("[\\s\u00a0]", "").replace(',', '.').trim();
        try {
            BigDecimal val = new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
            return val.compareTo(BigDecimal.ZERO) == 0 ? null : val;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parseDecimal(String s) {
        if (s == null || s.isBlank() || s.equals("-")) return null;
        s = s.replace(',', '.').trim();
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseTaux(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Integer.parseInt(s.replace("%", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String nullIfBlank(String s) {
        return (s == null || s.isBlank() || s.equals("-")) ? null : s;
    }
}
