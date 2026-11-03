package ma.medidispo.pharmacy_service.availability;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AvailabilityRequestIntegrationTest {
    @org.springframework.beans.factory.annotation.Value("${app.jwt.secret}")
    private String jwtSecret;


    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:16-3.5")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean AvailabilityPharmacySearchService pharmacySearch;

    private String token(UUID patientId, String role, long lifetimeMillis) {
        return io.jsonwebtoken.Jwts.builder()
                .subject(patientId.toString()).claim("role", role)
                .expiration(new java.util.Date(System.currentTimeMillis() + lifetimeMillis))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        io.jsonwebtoken.io.Decoders.BASE64.decode(
                                jwtSecret)))
                .compact();
    }

    @Test
    void patientRequestAndMedicationAreSavedInDatabase() throws Exception {
        UUID patientId = UUID.randomUUID();

        String authorization = "Bearer " + token(patientId, "PATIENT", 60000);

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
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
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

    @Test
    void onlyApprovedNearbyPharmaciesAreAssociated() throws Exception {
        UUID patientId = UUID.randomUUID();
        String authorization = "Bearer " + token(patientId, "PATIENT", 60000);

        UUID approvedNearby = UUID.randomUUID();
        UUID pendingNearby = UUID.randomUUID();
        UUID approvedFar = UUID.randomUUID();

        insertPharmacy(approvedNearby, "APPROVED", 31.630, -8.000);
        insertPharmacy(pendingNearby, "PENDING", 31.630, -8.000);
        insertPharmacy(approvedFar, "APPROVED", 33.590, -7.620);

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

        mvc.perform(post("/api/availability-requests")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        assertThat(associationCount(patientId, approvedNearby)).isEqualTo(1);
        assertThat(associationCount(patientId, pendingNearby)).isZero();
        assertThat(associationCount(patientId, approvedFar)).isZero();
    }

    private void insertPharmacy(UUID id, String status, double lat, double lng) {
        jdbc.update("""
                INSERT INTO pharmacies
                    (id, name, address, phone, opening_hours,
                     latitude, longitude, license_number, status)
                VALUES (?, ?, 'Adresse test', '0000000000', '09:00-18:00',
                        ?, ?,
                        ?, ?)
                """,
                id, "Pharmacie test " + id, lat, lng,
                "TEST-" + id, status);
    }

    private int associationCount(UUID patientId, UUID pharmacyId) {
        return jdbc.queryForObject("""
                SELECT count(*)
                FROM availability_request_pharmacies ap
                JOIN availability_requests ar ON ar.id = ap.request_id
                WHERE ar.patient_id = ?
                  AND ap.pharmacy_id = ?
                  AND ap.status = 'PENDING'
                """, Integer.class, patientId, pharmacyId);
    }

    @Test
    void realInvalidExpiredWrongRoleAndMissingTokensAreRejectedWithoutWrites() throws Exception {
        String body = """
                {"latitude":31.63,"longitude":-8,"radiusM":2000,
                 "medications":[{"name":"Doliprane","quantity":2}]}
                """;
        Long before = jdbc.queryForObject("SELECT count(*) FROM availability_requests", Long.class);
        mvc.perform(post("/api/availability-requests").contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isUnauthorized());
        for (String jwt : java.util.List.of("invalid.jwt.token",
                token(UUID.randomUUID(), "PATIENT", -60000),
                token(UUID.randomUUID(), "PHARMACY", 60000))) {
            mvc.perform(post("/api/availability-requests").contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + jwt).content(body))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM availability_requests", Long.class))
                .isEqualTo(before);
    }

    @Test
    void missingOrNullCoordinatesAndQuantityAreRejectedWithoutWrites() throws Exception {
        String authorization = "Bearer " + token(UUID.randomUUID(), "PATIENT", 60000);
        Long before = jdbc.queryForObject("SELECT count(*) FROM availability_requests", Long.class);
        String valid = """
                {"latitude":31.63,"longitude":-8,"radiusM":2000,
                 "medications":[{"name":"Doliprane","quantity":2}]}
                """;
        for (String body : java.util.List.of(
                valid.replace("\"latitude\":31.63,", ""),
                valid.replace("\"longitude\":-8", "\"longitude\":null"),
                valid.replace("\"quantity\":2", "\"quantity\":null"),
                valid.replace("\"radiusM\":2000", "\"radiusM\":null"))) {
            mvc.perform(post("/api/availability-requests").contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", authorization).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM availability_requests", Long.class))
                .isEqualTo(before);
    }

    private static final String VALID_BODY = """
            {"latitude":31.63,"longitude":-8,"radiusM":2000,
             "medications":[{"name":"Doliprane","quantity":2}]}
            """;

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("latitude below minimum", VALID_BODY.replace("31.63", "-90.01")),
                Arguments.of("latitude above maximum", VALID_BODY.replace("31.63", "90.01")),
                Arguments.of("longitude below minimum", VALID_BODY.replace("\"longitude\":-8", "\"longitude\":-180.01")),
                Arguments.of("longitude above maximum", VALID_BODY.replace("\"longitude\":-8", "\"longitude\":180.01")),
                Arguments.of("radius below minimum", VALID_BODY.replace("2000", "99")),
                Arguments.of("radius above maximum", VALID_BODY.replace("2000", "20001")),
                Arguments.of("fractional radius", VALID_BODY.replace("2000", "100.5")),
                Arguments.of("fractional quantity", VALID_BODY.replace("\"quantity\":2", "\"quantity\":1.5")),
                Arguments.of("zero quantity", VALID_BODY.replace("\"quantity\":2", "\"quantity\":0")),
                Arguments.of("negative quantity", VALID_BODY.replace("\"quantity\":2", "\"quantity\":-1")),
                Arguments.of("missing quantity", VALID_BODY.replace(",\"quantity\":2", "")),
                Arguments.of("blank name", VALID_BODY.replace("Doliprane", "   ")),
                Arguments.of("long name", VALID_BODY.replace("Doliprane", "Q".repeat(201))),
                Arguments.of("null name", VALID_BODY.replace("\"Doliprane\"", "null")),
                Arguments.of("missing name", VALID_BODY.replace("\"name\":\"Doliprane\",", "")),
                Arguments.of("null medication", VALID_BODY.replace("{\"name\":\"Doliprane\",\"quantity\":2}", "null")),
                Arguments.of("empty medications", VALID_BODY.replace("[{\"name\":\"Doliprane\",\"quantity\":2}]", "[]")),
                Arguments.of("null medications", VALID_BODY.replace("[{\"name\":\"Doliprane\",\"quantity\":2}]", "null")),
                Arguments.of("null request", "null"),
                Arguments.of("malformed JSON", "{"));
    }

    private java.util.List<Long> rowCounts() {
        return java.util.List.of(
                jdbc.queryForObject("SELECT count(*) FROM availability_requests", Long.class),
                jdbc.queryForObject("SELECT count(*) FROM availability_request_items", Long.class),
                jdbc.queryForObject("SELECT count(*) FROM availability_request_pharmacies", Long.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    void invalidInputIsRejectedWithoutAnyWrites(String scenario, String body) throws Exception {
        var before = rowCounts();
        mvc.perform(post("/api/availability-requests").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token(UUID.randomUUID(), "PATIENT", 60000))
                .content(body)).andExpect(status().isBadRequest());
        assertThat(rowCounts()).as(scenario).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PHARMACIEN", "ADMIN", "patient", ""})
    void nonPatientRolesAreRejectedWithoutAnyWrites(String role) throws Exception {
        var before = rowCounts();
        mvc.perform(post("/api/availability-requests").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token(UUID.randomUUID(), role, 60000))
                .content(VALID_BODY)).andExpect(status().isUnauthorized());
        assertThat(rowCounts()).isEqualTo(before);
    }

    @Test
    void signedPatientTokenWithoutSubjectIsRejected() throws Exception {
        String jwt = io.jsonwebtoken.Jwts.builder().claim("role", "PATIENT")
                .expiration(new java.util.Date(System.currentTimeMillis() + 60000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        io.jsonwebtoken.io.Decoders.BASE64.decode(
                                jwtSecret))).compact();
        var before = rowCounts();
        mvc.perform(post("/api/availability-requests").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + jwt).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        assertThat(rowCounts()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 20000})
    void radiusBoundariesPersistMultipleMedicinesAndOnlyApprovedPharmacies(int radius) throws Exception {
        UUID patientId = UUID.randomUUID();
        // A distinct position avoids fixtures created by other methods.
        double lat = 10 + radius / 10000.0;
        double lng = 20;
        UUID inside = UUID.randomUUID(), outside = UUID.randomUUID(), pending = UUID.randomUUID();
        insertPharmacy(inside, "APPROVED", lat, lng);
        insertPharmacy(outside, "APPROVED", lat + 1, lng);
        insertPharmacy(pending, "PENDING", lat, lng);
        // Project two APPROVED pharmacies to either side of the exact radius using PostGIS metres.
        UUID justInside = UUID.randomUUID(), justOutside = UUID.randomUUID();
        insertProjectedPharmacy(justInside, lat, lng, radius - 1);
        insertProjectedPharmacy(justOutside, lat, lng, radius + 1);
        String body = """
                {"latitude":%s,"longitude":%s,"radiusM":%d,
                 "medications":[{"name":"%s","quantity":1},{"name":"Vitamine C","quantity":3}]}
                """.formatted(lat, lng, radius, "Q".repeat(200));
        mvc.perform(post("/api/availability-requests").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token(patientId, "PATIENT", 60000)).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.patientId").value(patientId.toString()));
        assertThat(jdbc.queryForObject("SELECT status FROM availability_requests WHERE patient_id=?",
                String.class, patientId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForList("""
                SELECT i.medication_name, i.quantity FROM availability_request_items i
                JOIN availability_requests r ON r.id=i.request_id WHERE r.patient_id=? ORDER BY i.quantity
                """, patientId)).containsExactly(
                        java.util.Map.of("medication_name", "Q".repeat(200), "quantity", 1),
                        java.util.Map.of("medication_name", "Vitamine C", "quantity", 3));
        assertThat(associationCount(patientId, inside)).isEqualTo(1);
        assertThat(associationCount(patientId, justInside)).isEqualTo(1);
        assertThat(associationCount(patientId, justOutside)).isZero();
        assertThat(associationCount(patientId, outside)).isZero();
        assertThat(associationCount(patientId, pending)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM availability_request_pharmacies a JOIN availability_requests r
                ON r.id=a.request_id WHERE r.patient_id=?
                """, Integer.class, patientId)).isEqualTo(2);
    }

    private void insertProjectedPharmacy(UUID id, double lat, double lng, double distance) {
        var point = jdbc.queryForMap("""
                SELECT ST_Y(p::geometry) AS lat, ST_X(p::geometry) AS lng
                FROM (SELECT ST_Project(ST_SetSRID(ST_MakePoint(?, ?),4326)::geography, ?, 0) AS p) q
                """, lng, lat, distance);
        insertPharmacy(id, "APPROVED", ((Number) point.get("lat")).doubleValue(),
                ((Number) point.get("lng")).doubleValue());
    }

    @Test
    void requestIsSavedWhenNoPharmacyIsNearby() throws Exception {
        UUID patientId = UUID.randomUUID();
        mvc.perform(post("/api/availability-requests").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token(patientId, "PATIENT", 60000))
                .content(VALID_BODY.replace("31.63", "-80")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM availability_request_items i JOIN availability_requests r
                ON r.id=i.request_id WHERE r.patient_id=?
                """, Integer.class, patientId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM availability_request_pharmacies a JOIN availability_requests r
                ON r.id=a.request_id WHERE r.patient_id=?
                """, Integer.class, patientId)).isZero();
    }

    @Test
    void laterFailureRollsBackRequestAndMedicines() {
        var before = rowCounts();
        doThrow(new IllegalStateException("Simulated search failure"))
                .when(pharmacySearch).findApprovedNearby(31.63, -8, 2000);
        assertThatThrownBy(() -> mvc.perform(post("/api/availability-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token(UUID.randomUUID(), "PATIENT", 60000))
                .content(VALID_BODY))).hasCauseInstanceOf(IllegalStateException.class);
        assertThat(rowCounts()).isEqualTo(before);
    }
}
