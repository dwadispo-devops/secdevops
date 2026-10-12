package ma.secdevops.medication.importer;

import ma.secdevops.medication.model.Medication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Importeur du référentiel des médicaments CNOPS.
 * <p>
 * N'est actif que lorsque le profil Spring "import" est activé.
 * Lit le fichier Excel depuis le classpath, nettoie les données,
 * puis effectue un upsert en base par lots.
 * </p>
 *
 * <p><strong>Usage :</strong></p>
 * <pre>
 *   # Via Maven
 *   mvn spring-boot:run -Dspring-boot.run.profiles=import \
 *       -Dspring-boot.run.jvmArguments="-DDB_PASSWORD=..."
 *
 *   # Via JAR
 *   DB_PASSWORD=... java -jar medication-service.jar \
 *       --spring.profiles.active=import
 * </pre>
 *
 * <p>L'import est <strong>idempotent</strong> : le relancer ne crée pas de doublons
 * (INSERT ON CONFLICT DO UPDATE).</p>
 */
@Component
@Profile("import")
public class MedicationImportRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MedicationImportRunner.class);

    private final MedicationExcelReader reader;
    private final MedicationRepository repository;

    @Value("${medication.import.file:data/ref-des-medicaments-cnops-2014.xlsx}")
    private String importFile;

    @Value("${medication.import.batch-size:500}")
    private int batchSize;

    public MedicationImportRunner(MedicationExcelReader reader, MedicationRepository repository) {
        this.reader = reader;
        this.repository = repository;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("========================================================");
        log.info(" IMPORT DU RÉFÉRENTIEL MÉDICAMENTS CNOPS");
        log.info(" Fichier  : {}", importFile);
        log.info(" Taille lot : {}", batchSize);
        log.info("========================================================");

        ImportResult result = new ImportResult();

        // 1. Lecture et parsing du fichier Excel
        List<Medication> medications;
        try {
            medications = reader.read(importFile, result);
        } catch (Exception e) {
            log.error("Erreur fatale lors de la lecture du fichier Excel : {}", e.getMessage(), e);
            throw e;
        }

        if (medications.isEmpty()) {
            log.warn("Aucun médicament valide trouvé dans le fichier. Import annulé.");
            printSummary(result);
            return;
        }

        // 2. Import en base
        log.info("Début de l'import en base ({} médicaments valides)...", medications.size());
        int countBefore = repository.count();
        log.info("Médicaments en base avant import : {}", countBefore);

        int affected = repository.upsertAll(medications, batchSize);
        result.setLinesImported(affected);

        int countAfter = repository.count();
        log.info("Médicaments en base après import  : {}", countAfter);

        // 3. Rapport final
        printSummary(result);

        // 4. Afficher les lignes ignorées pour audit
        if (result.getIgnoredCount() > 0) {
            log.info("--- Détail des lignes ignorées ---");
            result.getIgnoredReasons().forEach(reason -> log.info("  IGNORÉE : {}", reason));
        }

        log.info("========================================================");
        log.info(" Import terminé avec succès.");
        log.info("========================================================");
    }

    private void printSummary(ImportResult result) {
        log.info("========================================================");
        log.info(" RAPPORT D'IMPORT");
        log.info("  Lignes lues dans le fichier : {}", result.getLinesRead());
        log.info("  Lignes importées (upsert)   : {}", result.getLinesImported());
        log.info("  Lignes ignorées             : {}", result.getIgnoredCount());
        log.info("========================================================");
    }
}
