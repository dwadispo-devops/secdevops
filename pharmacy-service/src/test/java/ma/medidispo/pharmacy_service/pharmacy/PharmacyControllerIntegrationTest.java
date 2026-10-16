package ma.medidispo.pharmacy_service.pharmacy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PharmacyControllerIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private String body(String name, String license) {
        return """
                {"name":"%s","address":"Adresse fictive","phone":"0000000000",
                 "openingHours":"Horaires fictifs","latitude":31.627,"longitude":-7.988,"licenseNumber":"%s"}
                """.formatted(name, license);
    }

    @Test
    void validMaximumLengthsAreAcceptedAndDuplicateLicenseIs409() throws Exception {
        String license = "Q".repeat(64) + UUID.randomUUID();
        String json = body("Q".repeat(200), license);
        mvc.perform(post("/api/pharmacies").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(post("/api/pharmacies").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pharmacies WHERE license_number = ?",
                Integer.class, license)).isEqualTo(1);
    }

    @Test
    void excessiveNameAndLicenseLengthsAreRejectedBeforeInsertion() throws Exception {
        String license = "QA-" + UUID.randomUUID();
        mvc.perform(post("/api/pharmacies").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Q".repeat(201), license))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/pharmacies").contentType(MediaType.APPLICATION_JSON)
                        .content(body("QA fictive", "Q".repeat(101)))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pharmacies WHERE license_number = ?",
                Integer.class, license)).isZero();
    }

    @Test
    void otherDatabaseConstraintsAreNotMisreportedAsLicenseConflict() {
        var cause = new org.hibernate.exception.ConstraintViolationException("fictitious constraint",
                new java.sql.SQLException("fictitious", "23505"), "pharmacies_osm_id_key");
        var response = new PharmacyExceptionHandler().handleIntegrityViolation(
                new org.springframework.dao.DataIntegrityViolationException("fictitious", cause));
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getDetail()).doesNotContain("SQL", "pharmacies_osm_id_key");
    }

    @Test
    void otherFieldLengthsAreValidated() throws Exception {
        String valid = body("QA fictive", "QA-" + UUID.randomUUID());
        for (String invalid : java.util.List.of(
                valid.replace("Adresse fictive", "Q".repeat(301)),
                valid.replace("0000000000", "Q".repeat(31)),
                valid.replace("Horaires fictifs", "Q".repeat(201)))) {
            mvc.perform(post("/api/pharmacies").contentType(MediaType.APPLICATION_JSON)
                    .content(invalid)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void invalidNearbyParametersRemain400() throws Exception {
        mvc.perform(get("/api/pharmacies/nearby").param("lat", "91").param("lng", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/pharmacies/nearby").param("lat", "0").param("lng", "0").param("radius", "99"))
                .andExpect(status().isBadRequest());
    }
}
