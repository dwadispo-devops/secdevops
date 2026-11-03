package ma.medidispo.pharmacy_service.stock;

import java.util.Date;
import java.util.UUID;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class StockControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
        DockerImageName.parse("postgis/postgis:16-3.5")
            .asCompatibleSubstituteFor("postgres"));

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private String token(UUID userId, String role) {
        return io.jsonwebtoken.Jwts.builder()
            .subject(userId.toString())
            .claim("role", role)
            .expiration(new Date(System.currentTimeMillis() + 60000))
            .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                io.jsonwebtoken.io.Decoders.BASE64.decode(
                    "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=")))
            .compact();
    }

    @Test
    void missingTokenReturns401() throws Exception {
        mvc.perform(put("/api/pharmacies/{id}/stock/123456",
                UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"AVAILABLE\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void patientRoleReturns403() throws Exception {
        mvc.perform(put("/api/pharmacies/{id}/stock/123456",
                UUID.randomUUID())
                .header("Authorization",
                    "Bearer " + token(UUID.randomUUID(), "PATIENT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"AVAILABLE\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanUpdateStockReturns204() throws Exception {
        UUID pharmacyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        jdbc.update("""
            INSERT INTO pharmacies
                (id, name, address, phone, opening_hours,
                 latitude, longitude, license_number, status, owner_user_id)
            VALUES (?, ?, 'Adresse test', '0000000000', '09:00-18:00',
                    31.63, -8.0, ?, 'APPROVED', ?)
            """, pharmacyId, "Pharmacie US11",
            "US11-HTTP-" + pharmacyId, ownerId);

        mvc.perform(put("/api/pharmacies/{id}/stock/123456", pharmacyId)
                .header("Authorization",
                    "Bearer " + token(ownerId, "PHARMACIEN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"AVAILABLE\"}"))
            .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("""
            SELECT status FROM pharmacy_stock
            WHERE pharmacy_id = ? AND medication_code = ?
            """, String.class, pharmacyId, "123456"))
            .isEqualTo("AVAILABLE");
    }
    @Test
    void nonOwnerReturns403WithoutWritingStock() throws Exception {
        UUID pharmacyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();

        jdbc.update("""
            INSERT INTO pharmacies
                (id, name, address, phone, opening_hours,
                 latitude, longitude, license_number, status, owner_user_id)
            VALUES (?, ?, 'Adresse test', '0000000000', '09:00-18:00',
                    31.63, -8.0, ?, 'APPROVED', ?)
            """, pharmacyId, "Pharmacie US11",
            "US11-SEC-" + pharmacyId, ownerId);

        mvc.perform(put("/api/pharmacies/{id}/stock/123456", pharmacyId)
                .header("Authorization",
                    "Bearer " + token(otherId, "PHARMACIEN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"AVAILABLE\"}"))
            .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM pharmacy_stock
            WHERE pharmacy_id = ?
            """, Integer.class, pharmacyId)).isZero();
    }

    @Test
    void invalidStatusReturns400() throws Exception {
        mvc.perform(put("/api/pharmacies/{id}/stock/123456",
                UUID.randomUUID())
                .header("Authorization",
                    "Bearer " + token(UUID.randomUUID(), "PHARMACIEN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INVALID\"}"))
            .andExpect(status().isBadRequest());
    }
}