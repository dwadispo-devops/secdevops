package ma.medidispo.pharmacy_service.alert;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** US22 / EF25 : alerte du patient quand un médicament revient en stock dans une pharmacie proche. */
@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class StockAlertIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
        DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Value("${app.jwt.secret}") String secret;

    UUID patient, patientLoin, pharmacien, pharmacie;
    String code, nomPharmacie;

    @BeforeEach void donnees() {
        patient = UUID.randomUUID(); patientLoin = UUID.randomUUID(); pharmacien = UUID.randomUUID();
        code = String.valueOf(6118000000000L + ThreadLocalRandom.current().nextLong(1, 999_999_999L));
        nomPharmacie = "Pharmacie alerte " + UUID.randomUUID();
        pharmacie = UUID.randomUUID();
        jdbc.update("INSERT INTO pharmacies (id,name,latitude,longitude,owner_user_id,status) VALUES (?,?,31.63,-8.0,?,'APPROVED')",
            pharmacie, nomPharmacie, pharmacien);
    }

    String token(UUID user, String role) {
        return "Bearer " + Jwts.builder().subject(user.toString()).claim("role", role)
            .expiration(new Date(System.currentTimeMillis() + 60000))
            .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))).compact();
    }
    ResultActions alerte(UUID p, double lat, double lng) throws Exception {
        return mvc.perform(post("/api/alertes").header("Authorization", token(p, "PATIENT"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"medicationCode\":\"" + code + "\",\"medicationName\":\"DOLIPRANE 500 MG\",\"latitude\":" + lat + ",\"longitude\":" + lng + "}"));
    }
    ResultActions stock(String statut) throws Exception {
        return mvc.perform(put("/api/pharmacies/" + pharmacie + "/stock/" + code)
            .header("Authorization", token(pharmacien, "PHARMACIEN"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + statut + "\"}"));
    }
    int notifications(UUID user) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'STOCK_DISPONIBLE'",
            Integer.class, user);
    }
    String statutAlerte(UUID p) {
        return jdbc.queryForObject("SELECT status FROM stock_alerts WHERE patient_id = ? AND medication_code = ?",
            String.class, p, code);
    }

    @Test void leRetourEnStockPrevientLesPatientsProchesUneSeuleFois() throws Exception {
        alerte(patient, 31.631, -8.001).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"));
        alerte(patientLoin, 33.57, -7.59).andExpect(status().isCreated()); // Casablanca : hors du rayon de 5 km

        stock("OUT_OF_STOCK").andExpect(status().isNoContent());
        stock("AVAILABLE").andExpect(status().isNoContent());

        assertThat(notifications(patient)).isEqualTo(1);
        assertThat(notifications(patientLoin)).isZero();
        assertThat(statutAlerte(patient)).isEqualTo("DECLENCHEE");
        assertThat(statutAlerte(patientLoin)).isEqualTo("ACTIVE");
        mvc.perform(get("/api/alertes/me").header("Authorization", token(patient, "PATIENT")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("DECLENCHEE"))
            .andExpect(jsonPath("$[0].pharmacyName").value(nomPharmacie));

        stock("OUT_OF_STOCK"); stock("AVAILABLE");
        assertThat(notifications(patient)).isEqualTo(1); // déjà déclenchée : pas de nouvelle notification
    }

    @Test void unMedicamentDejaEnStockNeDeclenchePas() throws Exception {
        stock("AVAILABLE").andExpect(status().isNoContent());
        alerte(patient, 31.631, -8.001).andExpect(status().isCreated());
        stock("AVAILABLE").andExpect(status().isNoContent()); // pas de changement de statut
        assertThat(notifications(patient)).isZero();
        assertThat(statutAlerte(patient)).isEqualTo("ACTIVE");
    }

    @Test void pasDeDoublonEtSeulLePatientSupprimeSonAlerte() throws Exception {
        String id = com.jayway.jsonpath.JsonPath.read(alerte(patient, 31.631, -8.001)
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        alerte(patient, 31.631, -8.001).andExpect(status().isConflict());
        mvc.perform(delete("/api/alertes/" + id).header("Authorization", token(patientLoin, "PATIENT")))
            .andExpect(status().isNotFound());
        mvc.perform(delete("/api/alertes/" + id).header("Authorization", token(patient, "PATIENT")))
            .andExpect(status().isNoContent());
    }

    @Test void reserveAuPatientEtDonneesValidees() throws Exception {
        mvc.perform(get("/api/alertes/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/alertes/me").header("Authorization", token(pharmacien, "PHARMACIEN")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/alertes").header("Authorization", token(patient, "PATIENT"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"medicationCode\":\"ABC\",\"medicationName\":\"X\",\"latitude\":31.6,\"longitude\":-8}"))
            .andExpect(status().isBadRequest());
    }
}
