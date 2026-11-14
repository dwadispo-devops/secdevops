package ma.medidispo.pharmacy_service.notification;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import ma.medidispo.pharmacy_service.reservation.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class NotificationIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationService notifications;
    @Autowired ReservationService reservations;
    @Value("${app.jwt.secret}") String secret;
    UUID patient, pharmacien, pharmacie, demande;

    @BeforeEach void donnees() {
        patient = UUID.randomUUID(); pharmacien = UUID.randomUUID();
        pharmacie = UUID.randomUUID(); demande = UUID.randomUUID();
        jdbc.update("INSERT INTO pharmacies (id,name,latitude,longitude,owner_user_id) VALUES (?,'Pharmacie Atlas',31.63,-8,?)", pharmacie, pharmacien);
        jdbc.update("INSERT INTO availability_requests (id,patient_id,latitude,longitude,radius_m) VALUES (?,?,31.63,-8,5000)", demande, patient);
        jdbc.update("INSERT INTO availability_request_pharmacies (request_id,pharmacy_id) VALUES (?,?)", demande, pharmacie);
    }
    String token(UUID user, String role) {
        return "Bearer " + Jwts.builder().subject(user.toString()).claim("role", role)
            .expiration(new Date(System.currentTimeMillis() + 60000)).signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))).compact();
    }
    List<String> types(UUID user) {
        return jdbc.queryForList("SELECT type FROM notifications WHERE user_id = ? ORDER BY created_at", String.class, user);
    }
    void repondreDisponible() throws Exception {
        mvc.perform(post("/api/pharmacies/" + pharmacie + "/requests/" + demande + "/response")
                .header("Authorization", token(pharmacien, "PHARMACIEN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"availability\":\"AVAILABLE\",\"price\":128.50}"))
            .andExpect(status().isOk());
    }
    UUID reserver() throws Exception {
        mvc.perform(post("/api/availability-requests/" + demande + "/reservations").header("Authorization", token(patient, "PATIENT"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"pharmacyId\":\"" + pharmacie + "\"}"))
            .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM reservations WHERE request_id = ?", UUID.class, demande);
    }

    @Test void lePatientEstPrevenuDeLaReponseSansNomDeMedicament() throws Exception {
        repondreDisponible();
        mvc.perform(get("/api/notifications/me").header("Authorization", token(patient, "PATIENT")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unread").value(1))
            .andExpect(jsonPath("$.items[0].type").value("REPONSE_PHARMACIE"))
            .andExpect(jsonPath("$.items[0].message").value("Pharmacie Atlas a vos médicaments (128.50 DH)."))
            .andExpect(jsonPath("$.items[0].link").value("/suivi-demande/" + demande));
    }

    @Test void onNePeutMarquerCommeLueQueSesPropresNotifications() throws Exception {
        repondreDisponible();
        UUID id = jdbc.queryForObject("SELECT id FROM notifications WHERE user_id = ?", UUID.class, patient);
        mvc.perform(post("/api/notifications/" + id + "/read").header("Authorization", token(UUID.randomUUID(), "PATIENT")))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/notifications/" + id + "/read").header("Authorization", token(patient, "PATIENT")))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/me").header("Authorization", token(patient, "PATIENT")))
            .andExpect(jsonPath("$.unread").value(0)).andExpect(jsonPath("$.items[0].read").value(true));
    }

    @Test void laReservationPrevientLePatientEtLePharmacien() throws Exception {
        repondreDisponible();
        reserver();
        assertThat(types(patient)).containsExactly("REPONSE_PHARMACIE", "RESERVATION_CONFIRMEE");
        assertThat(types(pharmacien)).containsExactly("NOUVELLE_RESERVATION");
        mvc.perform(get("/api/notifications/me").header("Authorization", token(patient, "PATIENT")))
            .andExpect(jsonPath("$.items[0].message").value(containsString("Pharmacie Atlas avant")));
    }

    @Test void leRetraitValideAuComptoirPrevientLePatient() throws Exception {
        repondreDisponible();
        UUID reservation = reserver();
        mvc.perform(post("/api/pharmacies/" + pharmacie + "/reservations/" + reservation + "/pickup")
                .header("Authorization", token(pharmacien, "PHARMACIEN")))
            .andExpect(status().isOk());
        assertThat(types(patient)).contains("RETRAIT_CONFIRME");
    }

    @Test void lExpirationPrevientLePatient() throws Exception {
        repondreDisponible();
        reserver();
        jdbc.update("UPDATE reservations SET expires_at = now() - interval '1 minute' WHERE request_id = ?", demande);
        reservations.expirer();
        assertThat(types(patient)).contains("RESERVATION_EXPIREE");
    }

    @Test void uneNouvelleDemandePrevientChaquePharmacienUneSeuleFois() {
        UUID sansPharmacien = UUID.randomUUID();
        jdbc.update("INSERT INTO pharmacies (id,name,latitude,longitude) VALUES (?,'Pharmacie OSM',31.63,-8)", sansPharmacien);
        notifications.nouvelleDemande(demande, List.of(pharmacie, pharmacie, sansPharmacien));
        assertThat(types(pharmacien)).containsExactly("NOUVELLE_DEMANDE");
    }

    @Test void sansJetonOnNeVoitRien() throws Exception {
        mvc.perform(get("/api/notifications/me")).andExpect(status().isUnauthorized());
    }
}
