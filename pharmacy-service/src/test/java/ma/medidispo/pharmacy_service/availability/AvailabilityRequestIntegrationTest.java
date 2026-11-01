package ma.medidispo.pharmacy_service.availability;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AvailabilityRequestIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:16-3.5")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean AvailabilityJwtService jwtService;

    @Test
    void patientRequestAndMedicationAreSavedInDatabase() throws Exception {
        UUID patientId = UUID.randomUUID();

        when(jwtService.authenticatedPatientId("Bearer test-token"))
                .thenReturn(patientId);

        String json = """
                {
                  "latitude": 31.63,
                  "longitude": -8.0,
                  "radiusM": 2000,
                  "medications": [
                    {"name": "Doliprane", "quantity": 2}
                  ]
                }
                """;

        String response = mvc.perform(
                post("/api/availability-requests")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientId").value(patientId.toString()))
                .andReturn().getResponse().getContentAsString();

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM availability_requests WHERE patient_id = ?",
                Integer.class, patientId)).isEqualTo(1);

        assertThat(jdbc.queryForObject(
                """
                SELECT count(*)
                FROM availability_request_items i
                JOIN availability_requests r ON r.id = i.request_id
                WHERE r.patient_id = ?
                  AND i.medication_name = 'Doliprane'
                  AND i.quantity = 2
                """,
                Integer.class, patientId)).isEqualTo(1);
    }
}