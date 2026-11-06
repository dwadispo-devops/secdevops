package ma.medidispo.pharmacy_service.reservation;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class ReservationIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReservationService service;
    @Value("${app.jwt.secret}") String secret;
    UUID patient, pharmacien, disponible, enAttente, request;

    @BeforeEach void donnees() {
        patient = UUID.randomUUID(); pharmacien = UUID.randomUUID();
        disponible = pharmacie("Pharmacie disponible"); enAttente = pharmacie("Pharmacie en attente");
        request = demande(patient);
        reponse(request, disponible, new BigDecimal("128.50"));
        jdbc.update("INSERT INTO availability_request_pharmacies (request_id,pharmacy_id) VALUES (?,?)", request, enAttente);
    }
    UUID pharmacie(String nom) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO pharmacies (id,name,latitude,longitude,owner_user_id) VALUES (?,?,31.63,-8,?)", id, nom, pharmacien);
        return id;
    }
    UUID demande(UUID p) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO availability_requests (id,patient_id,latitude,longitude,radius_m) VALUES (?,?,31.63,-8,5000)", id, p);
        return id;
    }
    void reponse(UUID req, UUID ph, BigDecimal prix) {
        jdbc.update("INSERT INTO availability_request_pharmacies (request_id,pharmacy_id,status,price,responded_at,responded_by) VALUES (?,?,'AVAILABLE',?,now(),?)",
            req, ph, prix, pharmacien);
    }
    String token(UUID user, String role) {
        return "Bearer " + Jwts.builder().subject(user.toString()).claim("role", role)
            .expiration(new Date(System.currentTimeMillis() + 60000)).signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))).compact();
    }
    ResultActions reserver(UUID req, UUID ph, String auth) throws Exception {
        var r = post("/api/availability-requests/" + req + "/reservations").contentType(MediaType.APPLICATION_JSON)
            .content("{\"pharmacyId\":\"" + ph + "\"}");
        return mvc.perform(auth == null ? r : r.header("Authorization", auth));
    }

    @Test void lePatientObtientUnCodeDeRetraitValableUneHeure() throws Exception {
        reserver(request, disponible, token(patient, "PATIENT")).andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value(matchesPattern("[A-HJ-NP-Z2-9]{6}")))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.price").value(128.5))
            .andExpect(jsonPath("$.pharmacyName").value("Pharmacie disponible"));
        Integer minutes = jdbc.queryForObject(
            "SELECT round(extract(epoch FROM expires_at - created_at) / 60)::int FROM reservations WHERE request_id = ?", Integer.class, request);
        assertThat(minutes).isEqualTo(60);
        mvc.perform(get("/api/availability-requests/" + request + "/reservation").header("Authorization", token(patient, "PATIENT")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test void refuseUnePharmacieQuiNaPasConfirme() throws Exception {
        reserver(request, enAttente, token(patient, "PATIENT")).andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(containsString("pas confirmé")));
    }

    @Test void uneSeuleReservationActiveParDemande() throws Exception {
        reserver(request, disponible, token(patient, "PATIENT")).andExpect(status().isCreated());
        reserver(request, disponible, token(patient, "PATIENT")).andExpect(status().isConflict());
    }

    @Test void auPlusTroisReservationsActives_RG04() throws Exception {
        for (int i = 0; i < 3; i++) {
            UUID autre = demande(patient);
            reponse(autre, disponible, BigDecimal.TEN);
            reserver(autre, disponible, token(patient, "PATIENT")).andExpect(status().isCreated());
        }
        reserver(request, disponible, token(patient, "PATIENT")).andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(containsString("3 réservations actives")));
    }

    @Test void securite_jetonRoleEtProprietaire() throws Exception {
        reserver(request, disponible, null).andExpect(status().isUnauthorized());
        reserver(request, disponible, token(pharmacien, "PHARMACIEN")).andExpect(status().isForbidden());
        reserver(request, disponible, token(UUID.randomUUID(), "PATIENT")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservations WHERE request_id = ?", Integer.class, request)).isZero();
    }

    @Test void expireApresUneHeurePuisPermetDeReserverANouveau_RG03() throws Exception {
        reserver(request, disponible, token(patient, "PATIENT")).andExpect(status().isCreated());
        jdbc.update("UPDATE reservations SET expires_at = now() - interval '1 minute' WHERE request_id = ?", request);
        mvc.perform(get("/api/availability-requests/" + request + "/reservation").header("Authorization", token(patient, "PATIENT")))
            .andExpect(jsonPath("$.status").value("EXPIREE"));
        service.expirer();
        assertThat(jdbc.queryForObject("SELECT status FROM reservations WHERE request_id = ?", String.class, request)).isEqualTo("EXPIREE");
        reserver(request, disponible, token(patient, "PATIENT")).andExpect(status().isCreated());
    }
}
