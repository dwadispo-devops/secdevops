package ma.secdevops.medication.importer;

import ma.secdevops.medication.model.Medication;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Lit le fichier Excel CNOPS et produit une liste de {@link Medication}.
 * <p>
 * Règles de nettoyage appliquées :
 * <ul>
 *   <li>CODE : conservé en texte (13 chiffres), pas de notation scientifique</li>
 *   <li>PPV / PH / PRIX_BR = 0 → NULL (données non applicables)</li>
 *   <li>DOSAGE1 = None ou vide → NULL ; virgule décimale convertie en point</li>
 *   <li>TAUX_REMBOURSEMENT : "70%" → 70, "0%" → 0</li>
 *   <li>Toute valeur String : trim() appliqué</li>
 *   <li>Ligne ignorée si CODE absent ou NOM absent</li>
 * </ul>
 * </p>
 */
@Component
public class MedicationExcelReader {

    private static final Logger log = LoggerFactory.getLogger(MedicationExcelReader.class);

    // Indices des colonnes dans le fichier Excel (0-based)
    private static final int COL_CODE = 0;
    private static final int COL_NOM = 1;
    private static final int COL_DCI1 = 2;
    private static final int COL_DOSAGE1 = 3;
    private static final int COL_UNITE = 4;
    private static final int COL_FORME = 5;
    private static final int COL_PRESENTATION = 6;
    private static final int COL_PPV = 7;
    private static final int COL_PH = 8;
    private static final int COL_PRIX_BR = 9;
    private static final int COL_PG = 10;
    private static final int COL_TAUX = 11;

    /**
     * Lit le fichier Excel depuis le classpath et retourne toutes les lignes valides.
     *
     * @param classpathPath chemin relatif dans le classpath (ex : "data/ref-des-medicaments.xlsx")
     * @param result        objet à remplir avec les statistiques de lecture
     * @return liste des médicaments valides
     */
    public List<Medication> read(String classpathPath, ImportResult result) throws IOException {
        try (InputStream is = new ClassPathResource(classpathPath).getInputStream()) {
            return read(is, result);
        }
    }

    /** Reads an Excel stream through the production parser. */
    public List<Medication> read(InputStream input, ImportResult result) throws IOException {
        List<Medication> medications = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(input)) {

            Sheet sheet = workbook.getSheetAt(0);
            int firstDataRow = sheet.getFirstRowNum() + 1; // ligne 0 = en-têtes
            int lastRow = sheet.getLastRowNum();

            log.info("Lecture du fichier Excel : {} ({} lignes de données attendues)",
                    "stream", lastRow - firstDataRow + 1);

            for (int i = firstDataRow; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    result.addIgnored("Ligne " + (i + 1) + " : ligne Excel vide (null)");
                    continue;
                }
                result.incrementRead();

                try {
                    Medication med = parseRow(row, i + 1);
                    if (med != null) {
                        medications.add(med);
                    } else {
                        // parseRow retourne null uniquement si la ligne est ignorée (déjà loguée)
                        result.addIgnored("Ligne " + (i + 1) + " : code ou nom invalide");
                    }
                } catch (Exception e) {
                    result.addIgnored("Ligne " + (i + 1) + " : erreur de parsing — " + e.getMessage());
                    log.warn("Ligne {} ignorée : {}", i + 1, e.getMessage());
                }
            }
        }

        log.info("Lecture terminée : {} lignes lues, {} valides, {} ignorées",
                result.getLinesRead(), medications.size(), result.getIgnoredCount());
        return medications;
    }

    /**
     * Parse une ligne Excel en objet {@link Medication}.
     * Retourne null si la ligne est invalide (code absent ou nom absent).
     */
    private Medication parseRow(Row row, int lineNum) {
        // --- CODE ---
        String code = readAsString(row, COL_CODE);
        if (code == null || code.isBlank()) {
            log.warn("Ligne {} ignorée : CODE absent", lineNum);
            return null;
        }
        // Nettoyage du code : supprimer espaces, garantir 13 chiffres
        code = code.replaceAll("\\s", "").replaceAll("\\.0$", "");
        if (!code.matches("\\d{13}")) {
            log.warn("Ligne {} ignorée : CODE invalide (attendu 13 chiffres, obtenu '{}')", lineNum, code);
            return null;
        }

        // --- NOM ---
        String nom = readAsString(row, COL_NOM);
        if (nom == null || nom.isBlank()) {
            log.warn("Ligne {} ignorée : NOM absent (code={})", lineNum, code);
            return null;
        }

        // --- Autres champs texte ---
        String dci1 = nullIfBlank(readAsString(row, COL_DCI1));
        String uniteDosage1 = nullIfBlank(readAsString(row, COL_UNITE));
        String forme = nullIfBlank(readAsString(row, COL_FORME));
        String presentation = nullIfBlank(readAsString(row, COL_PRESENTATION));

        // --- DOSAGE1 ---
        Cell dosageCell = row.getCell(COL_DOSAGE1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        String dosageBrut = dosageCell != null && dosageCell.getCellType() == CellType.STRING
                ? dosageCell.getStringCellValue() : readAsString(row, COL_DOSAGE1);
        if (dosageBrut != null && dosageBrut.isBlank()) dosageBrut = null;
        BigDecimal dosage1 = parseDecimal(readAsString(row, COL_DOSAGE1));

        // --- Prix (0 → NULL) ---
        BigDecimal ppv = parsePrixZeroToNull(row, COL_PPV, "PPV", lineNum, code);
        BigDecimal ph = parsePrixZeroToNull(row, COL_PH, "PH", lineNum, code);
        BigDecimal prixBr = parsePrixZeroToNull(row, COL_PRIX_BR, "PRIX_BR", lineNum, code);

        // --- PRINCEPS_GENERIQUE ---
        String pg = nullIfBlank(readAsString(row, COL_PG));
        if (pg != null && !pg.equals("P") && !pg.equals("G")) {
            log.warn("Ligne {} (code={}): PRINCEPS_GENERIQUE inattendu '{}', importé tel quel", lineNum, code, pg);
        }

        // --- TAUX_REMBOURSEMENT ---
        Integer taux = parseTaux(readAsString(row, COL_TAUX), lineNum, code);

        return new Medication(code, nom, dci1, dosage1, uniteDosage1, forme, presentation,
                ppv, ph, prixBr, pg, taux, dosageBrut);
    }

    // -------------------------------------------------------------------------
    // Méthodes utilitaires
    // -------------------------------------------------------------------------

    /**
     * Lit une cellule et retourne sa valeur en String (trim), ou null.
     * Gère les cellules numériques (évite la notation scientifique).
     */
    private String readAsString(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                // Pour éviter la notation scientifique sur les codes EAN
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    yield String.valueOf((long) d);
                }
                yield String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> null;
        };
    }

    /**
     * Lit un prix depuis une cellule. Si la valeur est 0 (ou nulle), retourne NULL.
     * Logge la conversion 0→NULL pour les stats.
     */
    private BigDecimal parsePrixZeroToNull(Row row, int colIndex, String colName, int lineNum, String code) {
        Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;

        if (cell.getCellType() == CellType.NUMERIC) {
            double val = cell.getNumericCellValue();
            if (val == 0.0) {
                log.debug("Ligne {} (code={}): {}=0 converti en NULL", lineNum, code, colName);
                return null;
            }
            return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP);
        }

        // Cellule texte (cas inhabituel pour les prix)
        String s = readAsString(row, colIndex);
        return parseDecimalPrix(s);
    }

    /**
     * Parse un BigDecimal depuis une String.
     * Gère la virgule décimale française (ex : "0,25" → 0.25).
     * Retourne null si la valeur est nulle, vide ou "-".
     */
    private BigDecimal parseDecimal(String s) {
        if (s == null || s.isBlank() || s.equals("-")) return null;
        // Remplacer virgule décimale par point
        s = s.replace(',', '.').trim();
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            log.debug("Dosage non scalaire conservé en texte : {}", s);
            return null;
        }
    }

    /**
     * Parse un prix texte (ex : "2 882,00", "2\u00a0882,00").
     * Retire espaces normaux et insécables, convertit virgule → point.
     * Retourne null si 0 ou vide.
     */
    private BigDecimal parseDecimalPrix(String s) {
        if (s == null || s.isBlank() || s.equals("-")) return null;
        // Supprimer espaces normaux et insécables (\u00a0) utilisés comme séparateurs de milliers
        s = s.replaceAll("[\\s\u00a0]", "").replace(',', '.').trim();
        try {
            BigDecimal val = new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
            return val.compareTo(BigDecimal.ZERO) == 0 ? null : val;
        } catch (NumberFormatException e) {
            log.warn("Impossible de parser le prix '{}' en décimal, retourné NULL", s);
            return null;
        }
    }

    /**
     * Parse le taux de remboursement : "70%" → 70, "0%" → 0.
     */
    private Integer parseTaux(String s, int lineNum, String code) {
        if (s == null || s.isBlank()) return null;
        String cleaned = s.replace("%", "").trim();
        try {
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            log.warn("Ligne {} (code={}): taux '{}' non parseable, retourné NULL", lineNum, code, s);
            return null;
        }
    }

    private String nullIfBlank(String s) {
        return (s == null || s.isBlank() || s.equals("-")) ? null : s;
    }
}
