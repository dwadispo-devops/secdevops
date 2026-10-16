package ma.ensa.pharma.prescription.service;

import ma.ensa.pharma.prescription.ai.OutputSanitizer;
import ma.ensa.pharma.prescription.ai.VisionReader;
import ma.ensa.pharma.prescription.api.dto.MedicationLineDto;
import ma.ensa.pharma.prescription.api.dto.ReadPrescriptionResponse;
import ma.ensa.pharma.prescription.domain.DetectedFileType;
import ma.ensa.pharma.prescription.domain.LineStatus;
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
 * modèle vision → validation de la sortie.
 * <p>
 * Aucune image ni aucun texte lu n'est stocké ou journalisé (RG06, ENF05) : seuls la durée
 * et le nombre de lignes apparaissent dans les logs.
 */
@Service
public class PrescriptionReadingService {

    private static final Logger log = LoggerFactory.getLogger(PrescriptionReadingService.class);
    static final long TARGET_DURATION_MS = 15_000;

    private final FileValidator fileValidator;
    private final ImagePreprocessor imagePreprocessor;
    private final VisionReader visionReader;
    private final OutputSanitizer outputSanitizer;

    public PrescriptionReadingService(FileValidator fileValidator,
                                      ImagePreprocessor imagePreprocessor,
                                      VisionReader visionReader,
                                      OutputSanitizer outputSanitizer) {
        this.fileValidator = fileValidator;
        this.imagePreprocessor = imagePreprocessor;
        this.visionReader = visionReader;
        this.outputSanitizer = outputSanitizer;
    }

    public ReadPrescriptionResponse read(byte[] content, String originalFilename, boolean consent) {
        if (!consent) {
            throw PrescriptionException.consentRequired();
        }
        long start = System.nanoTime();

        DetectedFileType type = fileValidator.validate(content, originalFilename);
        PreparedImage image = imagePreprocessor.prepare(content, type);
        VisionExtraction extraction = visionReader.read(image.png());
        List<MedicationLineDto> lines = outputSanitizer.sanitize(extraction);

        long durationMs = (System.nanoTime() - start) / 1_000_000;
        List<String> warnings = buildWarnings(extraction, lines, image, durationMs);

        log.info("Lecture d'ordonnance terminée : type={}, lignes={}, durée={} ms, fournisseur={}",
                type, lines.size(), durationMs, visionReader.providerName());

        return new ReadPrescriptionResponse(
                UUID.randomUUID(),
                extraction.prescriptionDetected(),
                lines,
                visionReader.providerName(),
                durationMs,
                warnings);
    }

    private static List<String> buildWarnings(VisionExtraction extraction, List<MedicationLineDto> lines,
                                              PreparedImage image, long durationMs) {
        List<String> warnings = new ArrayList<>();
        if (!extraction.prescriptionDetected()) {
            warnings.add("L'image ne semble pas être une ordonnance. Vérifiez la photo.");
        } else if (lines.isEmpty()) {
            warnings.add("Aucun médicament n'a été lu. Reprenez la photo ou saisissez vos médicaments à la main.");
        }
        if (lines.stream().anyMatch(l -> l.status() != LineStatus.LU)) {
            warnings.add("Certaines lignes sont incertaines ou illisibles : vérifiez-les avant de continuer.");
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
