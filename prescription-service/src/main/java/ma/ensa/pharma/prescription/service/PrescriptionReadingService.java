package ma.ensa.pharma.prescription.service;

import ma.ensa.pharma.prescription.ai.OutputSanitizer;
import ma.ensa.pharma.prescription.ai.VisionReader;
import ma.ensa.pharma.prescription.api.dto.LigneOrdonnanceDto;
import ma.ensa.pharma.prescription.api.dto.ReadPrescriptionResponse;
import ma.ensa.pharma.prescription.catalog.CatalogMatcher;
import ma.ensa.pharma.prescription.catalog.CatalogMatcher.MatchResult;
import ma.ensa.pharma.prescription.domain.DetectedFileType;
import ma.ensa.pharma.prescription.domain.ReadLine;
import ma.ensa.pharma.prescription.domain.VisionExtraction;
import ma.ensa.pharma.prescription.exception.PrescriptionException;
import ma.ensa.pharma.prescription.file.FileValidator;
import ma.ensa.pharma.prescription.file.ImagePreprocessor;
import ma.ensa.pharma.prescription.file.ImagePreprocessor.PreparedImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Orchestration de la lecture : consentement → validation du fichier → prétraitement →
 * modèle vision → validation de la sortie → rapprochement avec le catalogue CNOPS (US07).
 * <p>
 * Aucune image ni aucun texte lu n'est stocké ou journalisé (RG06, ENF05) : seuls la durée,
 * le nombre de lignes et la répartition des couleurs apparaissent dans les logs.
 */
@Service
public class PrescriptionReadingService {

    private static final Logger log = LoggerFactory.getLogger(PrescriptionReadingService.class);
    static final long TARGET_DURATION_MS = 15_000;
    static final double GREEN = 0.85;
    static final double ORANGE = 0.60;

    private final FileValidator fileValidator;
    private final ImagePreprocessor imagePreprocessor;
    private final VisionReader visionReader;
    private final OutputSanitizer outputSanitizer;
    private final CatalogMatcher catalogMatcher;

    public PrescriptionReadingService(FileValidator fileValidator,
                                      ImagePreprocessor imagePreprocessor,
                                      VisionReader visionReader,
                                      OutputSanitizer outputSanitizer,
                                      CatalogMatcher catalogMatcher) {
        this.fileValidator = fileValidator;
        this.imagePreprocessor = imagePreprocessor;
        this.visionReader = visionReader;
        this.outputSanitizer = outputSanitizer;
        this.catalogMatcher = catalogMatcher;
    }

    public ReadPrescriptionResponse read(byte[] content, String originalFilename, boolean consent) {
        if (!consent) {
            throw PrescriptionException.consentRequired();
        }
        long start = System.nanoTime();

        DetectedFileType type = fileValidator.validate(content, originalFilename);
        PreparedImage image = imagePreprocessor.prepare(content, type);
        VisionExtraction extraction = visionReader.read(image.png());
        List<ReadLine> lines = outputSanitizer.sanitize(extraction);
        MatchResult match = catalogMatcher.match(lines);

        long durationMs = (System.nanoTime() - start) / 1_000_000;
        List<String> warnings = buildWarnings(extraction, lines, match, image, durationMs);

        log.info("Lecture d'ordonnance terminée : type={}, lignes={}, vert={}, orange={}, rouge={}, durée={} ms, fournisseur={}",
                type, match.lignes().size(), count(match, GREEN, 2), count(match, ORANGE, GREEN),
                count(match, -1, ORANGE), durationMs, visionReader.providerName());

        return new ReadPrescriptionResponse(
                UUID.randomUUID(),
                extraction.prescriptionDetected(),
                match.lignes(),
                warnings,
                visionReader.providerName(),
                durationMs);
    }

    private static long count(MatchResult match, double min, double maxExclusive) {
        return match.lignes().stream()
                .mapToDouble(LigneOrdonnanceDto::score)
                .filter(s -> s >= min && s < maxExclusive)
                .count();
    }

    private static List<String> buildWarnings(VisionExtraction extraction, List<ReadLine> lines,
                                              MatchResult match, PreparedImage image, long durationMs) {
        List<String> warnings = new ArrayList<>();
        if (!extraction.prescriptionDetected()) {
            warnings.add("L'image ne semble pas être une ordonnance. Vérifiez la photo.");
        } else if (lines.isEmpty()) {
            warnings.add("Aucun médicament n'a été lu. Reprenez la photo ou saisissez vos médicaments à la main.");
        }
        if (match.catalogUnavailable()) {
            warnings.add("Le catalogue des médicaments est momentanément indisponible : "
                    + "choisissez vos médicaments à la main.");
        }
        if (lines.stream().anyMatch(ReadLine::illegible)) {
            warnings.add("Certaines lignes sont illisibles : saisissez-les à la main ou reprenez la photo.");
        }
        if (lines.stream().anyMatch(l -> !l.illegible() && l.quantity() == null)) {
            warnings.add("La quantité n'est pas indiquée pour certains médicaments : précisez-la avant de continuer.");
        }
        if (image.multiPage()) {
            warnings.add("Le PDF contient plusieurs pages : seule la première a été lue.");
        }
        if (durationMs > TARGET_DURATION_MS) {
            warnings.add("La lecture a dépassé l'objectif de 15 secondes.");
        }
        return warnings;
    }
}
