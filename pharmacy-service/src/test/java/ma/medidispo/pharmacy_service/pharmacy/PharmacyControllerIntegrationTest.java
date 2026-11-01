
package ma.medidispo.pharmacy_service.pharmacy;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PharmacyControllerIntegrationTest {

    private static final String TEST_SECRET =
            "c2VjcmV0LUEtc2VjcmV0LUEtc2VjcmV0LUEtc2VjcmV0LUEtMTIzNDU=";

    private static final SecretKey TEST_KEY =
            Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:16-3.5")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private String generateToken(String role) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "test@example.com")
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(TEST_KEY)
                .compact();
    }

    private String body(String name, String license) {
        return """
                {"name":"%s","address":"Adresse fictive","phone":"0000000000",
                 "openingHours":"Horaires fictifs","latitude":31.627,"longitude":-7.988,"licenseNumber":"%s"}
                """.formatted(name, license);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
    authenticatedPost(String json, String role) {

        return post("/api/pharmacies")
                .header("Authorization", "Bearer " + generateToken(role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    @Test
    void registrationWithoutTokenReturns401() throws Exception {
        mvc.perform(post("/api/pharmacies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Pharmacie test", "QA-" + UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patientCannotRegisterPharmacy() throws Exception {
        mvc.perform(authenticatedPost(
                        body("Pharmacie test", "QA-" + UUID.randomUUID()),
                        "PATIENT"))
                .andExpect(status().isForbidden());
    }

    @Test
    void pharmacistCanRegisterPharmacy() throws Exception {
        String license = "QA-" + UUID.randomUUID();

        mvc.perform(authenticatedPost(
                        body("Pharmacie test", license),
                        "PHARMACIEN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void validMaximumLengthsAreAcceptedAndDuplicateLicenseIs409()
            throws Exception {

        String license = "Q".repeat(64) + UUID.randomUUID();
        String json = body("Q".repeat(200), license);

        mvc.perform(authenticatedPost(json, "PHARMACIEN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mvc.perform(authenticatedPost(json, "PHARMACIEN"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pharmacies WHERE license_number = ?",
                Integer.class,
                license
        )).isEqualTo(1);
    }

    @Test
    void excessiveNameAndLicenseLengthsAreRejectedBeforeInsertion()
            throws Exception {

        String license = "QA-" + UUID.randomUUID();

        mvc.perform(authenticatedPost(
                        body("Q".repeat(201), license),
                        "PHARMACIEN"))
                .andExpect(status().isBadRequest());

        mvc.perform(authenticatedPost(
                        body("QA fictive", "Q".repeat(101)),
                        "PHARMACIEN"))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pharmacies WHERE license_number = ?",
                Integer.class,
                license
        )).isZero();
    }

    @Test
    void otherDatabaseConstraintsAreNotMisreportedAsLicenseConflict() {

        var cause = new org.hibernate.exception.ConstraintViolationException(
                "fictitious constraint",
                new java.sql.SQLException("fictitious", "23505"),
                "pharmacies_osm_id_key"
        );

        var response = new PharmacyExceptionHandler()
                .handleIntegrityViolation(
                        new org.springframework.dao.DataIntegrityViolationException(
                                "fictitious", cause
                        )
                );

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getDetail())
                .doesNotContain("SQL", "pharmacies_osm_id_key");
    }

    @Test
    void otherFieldLengthsAreValidated() throws Exception {

        String valid = body(
                "QA fictive",
                "QA-" + UUID.randomUUID()
        );

        for (String invalid : java.util.List.of(
                valid.replace("Adresse fictive", "Q".repeat(301)),
                valid.replace("0000000000", "Q".repeat(31)),
                valid.replace("Horaires fictifs", "Q".repeat(201))
        )) {
            mvc.perform(authenticatedPost(invalid, "PHARMACIEN"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void invalidNearbyParametersRemain400() throws Exception {

        mvc.perform(get("/api/pharmacies/nearby")
                        .param("lat", "91")
                        .param("lng", "0"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/pharmacies/nearby")
                        .param("lat", "0")
                        .param("lng", "0")
                        .param("radius", "99"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nearbySearchRemainsPublic() throws Exception {

        mvc.perform(get("/api/pharmacies/nearby")
                        .param("lat", "31.627")
                        .param("lng", "-7.988"))
                .andExpect(status().isOk());
    }
}
