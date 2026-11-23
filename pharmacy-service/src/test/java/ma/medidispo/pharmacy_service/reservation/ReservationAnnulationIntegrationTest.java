package ma.medidispo.pharmacy_service.reservation;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import ma.medidispo.pharmacy_service.review.PrescriptionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReservationAnnulationIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres")
    );

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReservationService service;
    @MockitoBean PrescriptionClient prescriptionClient;
    @Value("${app.jwt.secret}") String secret;

    UUID patient;
    UUID pharmacien;
    UUID pharmacie;
    UUID demande;
    UUID reservation;
    String code;

    @BeforeEach
    void setup() {
        patient = UUID.randomUUID();
        pharmacien = UUID.randomUUID();
        pharmacie = creerPharmacie(pharmacien);
        demande = creerDemande(patient);
        rendreDisponible(demande, pharmacie);
        code = "ABC234";
        reservation = insererReservation(demande, pharmacie, patient, code, "ACTIVE", 3600);
    }

    UUID creerPharmacie(UUID owner) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO pharmacies (id, name, latitude, longitude, owner_user_id) VALUES (?, ?, 31.63, -8.0, ?)",
                id, "Pharmacie de test", owner);
        return id;
    }

    UUID creerDemande(UUID patientId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO availability_requests (id, patient_id, latitude, longitude, radius_m) VALUES (?, ?, 31.63, -8.0, 5000)",
                id, patientId);
        return id;
    }

    void rendreDisponible(UUID demandeId, UUID pharmacieId) {
        jdbc.update("INSERT INTO availability_request_pharmacies (request_id, pharmacy_id, status, price, responded_at, responded_by) VALUES (?, ?, 'AVAILABLE', ?, now(), ?)",
                demandeId, pharmacieId, new BigDecimal("85.50"), pharmacien);
    }

    UUID insererReservation(UUID demandeId, UUID pharmacieId, UUID patientId, String pickupCode, String statut, int expirationSeconds) {
        return jdbc.queryForObject("""
            INSERT INTO reservations (request_id, pharmacy_id, patient_id, pickup_code, status, price, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, now() + (?::integer * interval '1 second'))
            RETURNING id
            """, UUID.class, demandeId, pharmacieId, patientId, pickupCode, statut, new BigDecimal("85.50"), expirationSeconds);
    }

    String token(UUID user, String role) {
        return "Bearer " + Jwts.builder().subject(user.toString()).claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))).compact();
    }

    String tokenExpire(UUID user) {
        return "Bearer " + Jwts.builder().subject(user.toString()).claim("role", "PATIENT")
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))).compact();
    }

    String chemin(UUID demandeId, UUID reservationId) {
        return "/api/availability-requests/" + demandeId + "/reservations/" + reservationId + "/cancel";
    }

    @Test
    void annulationReussie_metAJourReponseEtBase() throws Exception {
        mvc.perform(post(chemin(demande, reservation)).header("Authorization", token(patient, "PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reservation.toString()))
                .andExpect(jsonPath("$.requestId").value(demande.toString()))
                .andExpect(jsonPath("$.status").value("ANNULEE"));

        assertThat(jdbc.queryForObject("SELECT status FROM reservations WHERE id = ?", String.class, reservation))
                .isEqualTo("ANNULEE");
    }

    @Test
    void annulationLibereLeQuota_RG04() throws Exception {
        for (int i = 0; i < 2; i++) {
            UUID autreDemande = creerDemande(patient);
            rendreDisponible(autreDemande, pharmacie);
            insererReservation(autreDemande, pharmacie, patient, "Q" + i + "C234", "ACTIVE", 3600);
        }
        UUID demandeNouvelle = creerDemande(patient);
        rendreDisponible(demandeNouvelle, pharmacie);

        mvc.perform(post(chemin(demande, reservation)).header("Authorization", token(patient, "PATIENT")))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservations WHERE patient_id = ? AND status = 'ACTIVE'",
                Integer.class, patient)).isEqualTo(2);
        mvc.perform(post("/api/availability-requests/" + demandeNouvelle + "/reservations")
                        .header("Authorization", token(patient, "PATIENT"))
                        .contentType("application/json")
                        .content("{\"pharmacyId\":\"" + pharmacie + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void nouvelleReservationPossibleSurLaMemeDemandeApresAnnulation() throws Exception {
        mvc.perform(post(chemin(demande, reservation)).header("Authorization", token(patient, "PATIENT")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/availability-requests/" + demande + "/reservations")
                        .header("Authorization", token(patient, "PATIENT"))
                        .contentType("application/json")
                        .content("{\"pharmacyId\":\"" + pharmacie + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void codeAnnuleIntrouvablePourRechercheEtRetrait() throws Exception {
        mvc.perform(post(chemin(demande, reservation)).header("Authorization", token(patient, "PATIENT")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/pharmacies/" + pharmacie + "/reservations/" + code)
                        .header("Authorization", token(pharmacien, "PHARMACIEN")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/pharmacies/" + pharmacie + "/reservations/" + reservation + "/pickup")
                        .header("Authorization", token(pharmacien, "PHARMACIEN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void reservationDUnAutrePatient_retourne404Uniforme() throws Exception {
        UUID autrePatient = UUID.randomUUID();
        UUID autreDemande = creerDemande(autrePatient);
        rendreDisponible(autreDemande, pharmacie);
        UUID autreReservation = insererReservation(autreDemande, pharmacie, autrePatient, "OTH234", "ACTIVE", 3600);

        mvc.perform(post(chemin(autreDemande, autreReservation)).header("Authorization", token(patient, "PATIENT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Réservation introuvable"));
    }

    @Test
    void reservationInconnue_retourne404() throws Exception {
        mvc.perform(post(chemin(demande, UUID.randomUUID())).header("Authorization", token(patient, "PATIENT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Réservation introuvable"));
    }

    @Test
    void demandeDuCheminDifferenteDeLaReservation_retourne404() throws Exception {
        UUID autreDemande = creerDemande(patient);
        rendreDisponible(autreDemande, pharmacie);

        mvc.perform(post(chemin(autreDemande, reservation)).header("Authorization", token(patient, "PATIENT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Réservation introuvable"));
    }

    @Test
    void reservationDejaAnnulee_retourne409() throws Exception {
        jdbc.update("UPDATE reservations SET status = 'ANNULEE' WHERE id = ?", reservation);
        mvc.perform(post(chemin(demande, reservation)).header("Authorization", token(patient, "PATIENT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cette réservation ne peut plus être annulée."));
    }

    @Test
    void reservationDejaRetiree_retourne409() throws Exception {
        jdbc.update("UPDATE reservations SET status = 'RETIREE', picked_up_at = now() WHERE id = ?", reservation);
        mvc.perform(post(chemin(demande, reservation)).header("Authorization", token(patient, "PATIENT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cette réservation ne peut plus être annulée."));
    }

    @Test
    void reservationExpireeAvantEtApresExpireDue_retourne409() throws Exception {
        jdbc.update("UPDATE reservations SET expires_at = now() - interval '1 second' WHERE id = ?", reservation);
        mvc.perform(post(chemin(demande, reservation)).header("Authorization", token(patient, "PATIENT")))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT status FROM reservations WHERE id = ?", String.class, reservation))
                .isEqualTo("ACTIVE");

        service.expirer();
        assertThat(jdbc.queryForObject("SELECT status FROM reservations WHERE id = ?", String.class, reservation))
                .isEqualTo("EXPIREE");
        mvc.perform(post(chemin(demande, reservation)).header("Authorization", token(patient, "PATIENT")))
                .andExpect(status().isConflict());
    }

    @Test
    void securite_rolePharmacienSansJetonEtJetonsInvalides() throws Exception {
        String url = chemin(demande, reservation);
        mvc.perform(post(url).header("Authorization", token(pharmacien, "PHARMACIEN")))
                .andExpect(status().isForbidden());
        mvc.perform(post(url)).andExpect(status().isUnauthorized());
        mvc.perform(post(url).header("Authorization", "Bearer invalide"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(url).header("Authorization", tokenExpire(patient)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void annulationNeCreeAucuneNotification() throws Exception {
        int notificationsAvant = jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class);
        mvc.perform(post(chemin(demande, reservation)).header("Authorization", token(patient, "PATIENT")))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class))
                .isEqualTo(notificationsAvant);
    }

    @Test
    void annulationNeModifieAucuneAutreReservationDuPatient() throws Exception {
        UUID autreDemande = creerDemande(patient);
        rendreDisponible(autreDemande, pharmacie);
        UUID autreReservation = insererReservation(autreDemande, pharmacie, patient, "OTH234", "ACTIVE", 3600);

        mvc.perform(post(chemin(demande, reservation)).header("Authorization", token(patient, "PATIENT")))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT status FROM reservations WHERE id = ?", String.class, reservation))
                .isEqualTo("ANNULEE");
        assertThat(jdbc.queryForObject("SELECT status FROM reservations WHERE id = ?", String.class, autreReservation))
                .isEqualTo("ACTIVE");
    }

    @Test
    void courseAnnulationRetrait_uneSeuleOperationReussit() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var depart = new CountDownLatch(1);
        try {
            var annulation = pool.submit(() -> {
                depart.await();
                return mvc.perform(post(chemin(demande, reservation))
                                .header("Authorization", token(patient, "PATIENT")))
                        .andReturn().getResponse().getStatus();
            });
            var retrait = pool.submit(() -> {
                depart.await();
                return mvc.perform(post("/api/pharmacies/" + pharmacie + "/reservations/" + reservation + "/pickup")
                                .header("Authorization", token(pharmacien, "PHARMACIEN")))
                        .andReturn().getResponse().getStatus();
            });
            depart.countDown();
            int statutAnnulation = annulation.get();
            int statutRetrait = retrait.get();

            assertThat((statutAnnulation == 200 ? 1 : 0) + (statutRetrait == 200 ? 1 : 0)).isEqualTo(1);
            assertThat(statutAnnulation == 200 || statutAnnulation == 404 || statutAnnulation == 409).isTrue();
            assertThat(statutRetrait == 200 || statutRetrait == 404 || statutRetrait == 409).isTrue();
            assertThat(jdbc.queryForObject("SELECT status FROM reservations WHERE id = ?", String.class, reservation))
                    .isIn("ANNULEE", "RETIREE");
        } finally {
            pool.shutdownNow();
        }
    }
}
