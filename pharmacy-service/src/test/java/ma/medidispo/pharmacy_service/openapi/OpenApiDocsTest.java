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

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** TS07 : la documentation OpenAPI est servie, complète, et n'ouvre pas les API protégées. */
@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class OpenApiDocsTest {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
        DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));
    @Autowired MockMvc mvc;

    @Test void laDocumentationDecritLesApisLeJwtEtLaGateway() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.info.title").value(startsWith("DwaDispo")))
            .andExpect(jsonPath("$.paths['/api/alertes']").exists())
            .andExpect(jsonPath("$.paths['/api/pharmacies/nearby']").exists())
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
            .andExpect(jsonPath("$.servers[0].url").value("/"));
    }

    @Test void laPageSwaggerSOuvreSansJetonMaisLesApisRestentProtegees() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/api/alertes/me")).andExpect(status().isUnauthorized());
    }
}
