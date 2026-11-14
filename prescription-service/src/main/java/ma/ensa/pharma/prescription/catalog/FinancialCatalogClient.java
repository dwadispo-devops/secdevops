package ma.ensa.pharma.prescription.catalog;

import ma.ensa.pharma.prescription.config.CatalogProperties;
import ma.ensa.pharma.prescription.exception.PrescriptionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class FinancialCatalogClient {
    public record Medication(String code, String nom, String presentation, BigDecimal ppv, Integer tauxRemboursement) {}
    private final RestClient client;

    @Autowired
    public FinancialCatalogClient(RestClient.Builder builder, CatalogProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.timeout());
        factory.setReadTimeout(properties.timeout());
        client = builder.baseUrl(properties.baseUrl()).requestFactory(factory).build();
    }
    FinancialCatalogClient(RestClient client) { this.client = client; }

    public List<Medication> lookup(List<String> codes) {
        try {
            var result = client.post().uri("/api/medications/lookup").body(Map.of("codes", codes))
                    .retrieve().body(new ParameterizedTypeReference<List<Medication>>() {});
            if (result == null) throw new PrescriptionException(HttpStatus.SERVICE_UNAVAILABLE,
                    "CATALOG_UNAVAILABLE", "Le catalogue est indisponible. Réessayez plus tard.");
            return result;
        } catch (RestClientException ex) {
            // Ne pas journaliser le corps, les codes ou l'exception HTTP (données d'ordonnance).
            throw new PrescriptionException(HttpStatus.SERVICE_UNAVAILABLE, "CATALOG_UNAVAILABLE",
                    "Le catalogue est indisponible. Réessayez plus tard.");
        }
    }
}
