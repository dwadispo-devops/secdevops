package ma.ensa.pharma.prescription.catalog;

import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ma.ensa.pharma.prescription.exception.PrescriptionException;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class FinancialCatalogClientTest {
    MockRestServiceServer server;
    FinancialCatalogClient client;
    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("http://catalog");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FinancialCatalogClient(builder.build());
    }
    @Test void batchContractPreservesNullsAndDoesNotUsePrixBr() {
        server.expect(requestTo("http://catalog/api/medications/lookup")).andExpect(method(HttpMethod.POST))
            .andExpect(content().json("{\"codes\":[\"6118000000001\"]}"))
            .andRespond(withSuccess("""
                [{"code":"6118000000001","nom":"Produit","presentation":"1 BOITE",
                  "ppv":null,"tauxRemboursement":null}]
                """, MediaType.APPLICATION_JSON));
        var result = client.lookup(List.of("6118000000001"));
        assertThat(result.getFirst().ppv()).isNull();
        assertThat(result.getFirst().tauxRemboursement()).isNull();
        server.verify();
    }
    @Test void missingCodesStayMissing() {
        server.expect(anything()).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThat(client.lookup(List.of("6118000000001"))).isEmpty();
    }
    @Test void networkFailureIs503WithoutPayloadInMessage() {
        server.expect(anything()).andRespond(withException(new java.net.SocketTimeoutException()));
        assertThatThrownBy(() -> client.lookup(List.of("6118000000001")))
            .isInstanceOfSatisfying(PrescriptionException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(e.getMessage()).doesNotContain("6118000000001");
            });
    }
    @Test void serverErrorDoesNotProduceFakePrices() {
        server.expect(anything()).andRespond(withServerError());
        assertThatThrownBy(() -> client.lookup(List.of("6118000000001"))).isInstanceOf(PrescriptionException.class);
    }
}
