package ma.ensa.pharma.prescription.catalog;

import ma.ensa.pharma.prescription.config.CatalogProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Client HTTP de l'API de recherche de medication-service (US05) :
 * {@code GET /api/medications/search?q=...&limit=...}.
 * <p>
 * Cette API trie déjà les résultats par pertinence (préfixe, sous-chaîne, similarité trigramme
 * pg_trgm). Le score de confiance de US07 est calculé ensuite par {@link MatchScorer}.
 */
@Component
public class MedicationCatalogClient implements CatalogClient {

    private static final Logger log = LoggerFactory.getLogger(MedicationCatalogClient.class);
    private static final ParameterizedTypeReference<List<CatalogMedication>> LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    @Autowired
    public MedicationCatalogClient(RestClient.Builder builder, CatalogProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /** Constructeur pour les tests (RestClient branché sur un faux serveur). */
    MedicationCatalogClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public List<CatalogMedication> search(String query, int limit) {
        try {
            List<CatalogMedication> result = restClient.get()
                    .uri(uri -> uri.path("/api/medications/search")
                            .queryParam("q", query)
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .body(LIST_TYPE);
            return result == null ? List.of() : result;
        } catch (RestClientException e) {
            // On ne journalise pas le texte recherché (donnée issue d'une ordonnance, ENF05)
            log.warn("Catalogue des médicaments indisponible : {}", e.getClass().getSimpleName());
            throw new CatalogUnavailableException("Catalogue indisponible", e);
        }
    }
}
