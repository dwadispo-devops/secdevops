package ma.medidispo.pharmacy_service.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TS07 / DevSecOps : avec SWAGGER_ENABLED=false (production), la documentation n'est plus servie. */
@SpringBootTest(properties = {"springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
@AutoConfigureMockMvc @Testcontainers
class OpenApiDocsDisabledTest {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
        DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));
    @Autowired MockMvc mvc;

    @Test void enProductionLaDocumentationEstCoupee() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }
}
