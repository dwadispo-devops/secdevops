package ma.medidispo.pharmacy_service.osm;

import ma.medidispo.pharmacy_service.pharmacy.PharmacyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@ConditionalOnProperty(name = "osm.import.enabled", havingValue = "true")
public class OsmPharmacyImportRunner implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(OsmPharmacyImportRunner.class);

    private final OsmPharmacyImportService importService;
    private final PharmacyRepository repository;

    @Value("${osm.import.file:}")
    private String filePath;

    @Value("${osm.import.only-if-empty:true}")
    private boolean onlyIfEmpty;

    public OsmPharmacyImportRunner(
            OsmPharmacyImportService importService,
            PharmacyRepository repository
    ) {
        this.importService = importService;
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {

        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException(
                    "Veuillez préciser le fichier avec osm.import.file"
            );
        }

        if (onlyIfEmpty && repository.count() > 0) {
            log.info("Import OSM ignoré : la table contient déjà des pharmacies.");
            return;
        }

        OsmPharmacyImportService.ImportResult result =
                importService.importFromFile(Path.of(filePath));

        log.info("Import OSM terminé : {} insérées, {} ignorées",
                result.inserted(), result.skipped());
    }
}
