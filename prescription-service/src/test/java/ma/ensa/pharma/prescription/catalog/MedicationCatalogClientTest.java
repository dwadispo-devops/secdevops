package ma.ensa.pharma.prescription.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Vérifie l'appel HTTP vers medication-service avec un faux serveur (format réel de l'API US05). */
class MedicationCatalogClientTest {

    private MockRestServiceServer server;
    private MedicationCatalogClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://medication-service");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MedicationCatalogClient(builder.build());
    }

    @Test
    void callsSearchApiAndReadsCnopsFields() {
        server.expect(requestTo("http://medication-service/api/medications/search?q=DOLIPRANE&limit=10"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {
                            "code": "6118000040279",
                            "nom": "DOLIPRANE",
                            "dci1": "PARACETAMOL",
                            "dosage1": 300.000,
                            "uniteDosage1": "MG",
                            "forme": "POUDRE POUR SOLUTION BUVABLE",
                            "presentation": "1 BOITE 12 SACHET",
                            "ppv": 12.85,
                            "tauxRemboursement": 70,
                            "princepsGenerique": "P"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<CatalogMedication> result = client.search("DOLIPRANE", 10);

        assertThat(result).hasSize(1);
        CatalogMedication med = result.get(0);
        assertThat(med.code()).isEqualTo("6118000040279");
        assertThat(med.nom()).isEqualTo("DOLIPRANE");
        assertThat(med.dosage1()).isEqualByComparingTo(new BigDecimal("300"));
        assertThat(med.uniteDosage1()).isEqualTo("MG");
        assertThat(med.label()).isEqualTo("DOLIPRANE 300 MG, POUDRE POUR SOLUTION BUVABLE, 1 BOITE 12 SACHET");
        server.verify();
    }

    @Test
    void emptyResultGivesEmptyList() {
        server.expect(requestTo("http://medication-service/api/medications/search?q=XYZ&limit=10"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.search("XYZ", 10)).isEmpty();
    }

    @Test
    void serverErrorBecomesCatalogUnavailable() {
        server.expect(requestTo("http://medication-service/api/medications/search?q=DOLIPRANE&limit=10"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.search("DOLIPRANE", 10))
                .isInstanceOf(CatalogUnavailableException.class);
    }
}
