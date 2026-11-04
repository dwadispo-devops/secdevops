
package ma.medidispo.pharmacy_service.osm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.medidispo.pharmacy_service.pharmacy.Pharmacy;
import ma.medidispo.pharmacy_service.pharmacy.PharmacyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;

@Service
public class OsmPharmacyImportService {

    private final ObjectMapper objectMapper;
    private final PharmacyRepository pharmacyRepository;

    public OsmPharmacyImportService(
            ObjectMapper objectMapper,
            PharmacyRepository pharmacyRepository
    ) {
        this.objectMapper = objectMapper;
        this.pharmacyRepository = pharmacyRepository;
    }

    @Transactional
    public ImportResult importFromFile(Path file) {

        int inserted = 0;
        int skipped = 0;

        try {
            JsonNode root = objectMapper.readTree(file.toFile());
            JsonNode features = root.path("features");

            if (!features.isArray()) {
                throw new IllegalArgumentException(
                        "Le fichier GeoJSON ne contient pas de tableau features."
                );
            }

            for (JsonNode feature : features) {

                // Nous importons uniquement les objets Point.
                JsonNode geometry = feature.path("geometry");

                if (!"Point".equals(geometry.path("type").asText())) {
                    skipped++;
                    continue;
                }

                // Identifiant OSM : seuls les nœuds (n...) sont retenus.
                String featureId = feature.path("id").asText("");

                if (!featureId.matches("n[0-9]+")) {
                    skipped++;
                    continue;
                }

                Long osmId;

                try {
                    osmId = Long.parseLong(featureId.substring(1));
                } catch (NumberFormatException exception) {
                    skipped++;
                    continue;
                }

                JsonNode properties = feature.path("properties");

                String name = properties.path("name").asText("").trim();

                if (name.isBlank()) {
                    skipped++;
                    continue;
                }

                // GeoJSON : coordonnées dans l'ordre [longitude, latitude].
                JsonNode coordinates = geometry.path("coordinates");

                if (!coordinates.isArray() || coordinates.size() < 2
                        || !coordinates.get(0).isNumber()
                        || !coordinates.get(1).isNumber()) {
                    skipped++;
                    continue;
                }

                double longitude = coordinates.get(0).asDouble();
                double latitude = coordinates.get(1).asDouble();

                if (longitude < -180 || longitude > 180
                        || latitude < -90 || latitude > 90) {
                    skipped++;
                    continue;
                }

                // Ne jamais écraser une pharmacie déjà présente en base.
                if (pharmacyRepository.existsByOsmId(osmId)) {
                    skipped++;
                    continue;
                }

                String address = extractAddress(properties);
                String phone = properties.path("phone").asText(null);
                String openingHours =
                        properties.path("opening_hours").asText(null);

                Pharmacy pharmacy = new Pharmacy(
                        name,
                        address,
                        phone,
                        openingHours,
                        latitude,
                        longitude,
                        osmId,
                        "OSM"
                );

                pharmacyRepository.save(pharmacy);
                inserted++;
            }

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Impossible de lire le fichier GeoJSON : " + file,
                    exception
            );
        }

        return new ImportResult(inserted, skipped);
    }

    private String extractAddress(JsonNode properties) {

        String fullAddress = properties.path("addr:full").asText("");

        if (!fullAddress.isBlank()) {
            return fullAddress;
        }

        String houseNumber =
                properties.path("addr:housenumber").asText("");

        String street = properties.path("addr:street").asText("");
        String city = properties.path("addr:city").asText("");

        StringBuilder address = new StringBuilder();

        if (!houseNumber.isBlank()) {
            address.append(houseNumber).append(" ");
        }

        if (!street.isBlank()) {
            address.append(street);
        }

        if (!city.isBlank()) {
            if (!address.isEmpty()) {
                address.append(", ");
            }
            address.append(city);
        }

        return address.isEmpty() ? null : address.toString();
    }

    public record ImportResult(int inserted, int skipped) {
    }
}
