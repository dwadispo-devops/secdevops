
package ma.medidispo.pharmacy_service.osm;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@ConditionalOnProperty(
        name = "osm.import.enabled",
        havingValue = "true"
)
public class OsmPharmacyImportRunner implements ApplicationRunner {

    private final OsmPharmacyImportService importService;

    public OsmPharmacyImportRunner(
            OsmPharmacyImportService importService
    ) {
        this.importService = importService;
    }

    @Override
    public void run(ApplicationArguments args) {

        String filePath = args.containsOption("osm.import.file")
                ? args.getOptionValues("osm.import.file").get(0)
                : null;

        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException(
                    "Veuillez préciser le fichier avec --osm.import.file"
            );
        }

        OsmPharmacyImportService.ImportResult result =
                importService.importFromFile(Path.of(filePath));

        System.out.println("========== IMPORT OSM TERMINÉ ==========");
        System.out.println("Pharmacies insérées : " + result.inserted());
        System.out.println("Pharmacies ignorées : " + result.skipped());
        System.out.println("========================================");
    }
}
