package ma.medidispo.pharmacy_service.reservation;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests d'intégration pour l'US13 : recherche par code de retrait et validation de retrait par le pharmacien.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReservationRetraitIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres")
    );

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReservationService service;
    @Value("${app.jwt.secret}") String secret;

    UUID pharmacienProprietaire, autrePharmacien, patient;
    UUID maPharmacie, autrePharmacie;
    UUID demande;
    String codeActif;
    UUID reservationId;

    @BeforeEach
    void setup() {
        pharmacienProprietaire = UUID.randomUUID();
        autrePharmacien = UUID.randomUUID();
        patient = UUID.randomUUID();

        maPharmacie = creerPharmacie("Pharmacie du Centre", pharmacienProprietaire);
        autrePharmacie = creerPharmacie("Autre Pharmacie", autrePharmacien);

        demande = creerDemande(patient);
        creerItems(demande, "Doliprane 1000mg", 2);
        creerItems(demande, "Amoxicilline 500mg", 1);

        repondreDisponible(demande, maPharmacie, new BigDecimal("85.50"));
        repondreDisponible(demande, autrePharmacie, new BigDecimal("90.00"));

        codeActif = "ABC234";
        reservationId = insererReservation(demande, maPharmacie, patient, codeActif, "ACTIVE", 3600, new BigDecimal("85.50"));
    }

    UUID creerPharmacie(String nom, UUID proprietaire) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO pharmacies (id, name, latitude, longitude, owner_user_id) VALUES (?, ?, 31.63, -8.0, ?)",
                id, nom, proprietaire);
        return id;
    }

    UUID creerDemande(UUID patientId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO availability_requests (id, patient_id, latitude, longitude, radius_m) VALUES (?, ?, 31.63, -8.0, 5000)",
                id, patientId);
        return id;
    }

    void creerItems(UUID reqId, String medicament, int quantite) {
        jdbc.update("INSERT INTO availability_request_items (request_id, medication_name, quantity) VALUES (?, ?, ?)",
                reqId, medicament, quantite);
    }

    void repondreDisponible(UUID reqId, UUID phId, BigDecimal prix) {
        jdbc.update("INSERT INTO availability_request_pharmacies (request_id, pharmacy_id, status, price, responded_at, responded_by) " +
                "VALUES (?, ?, 'AVAILABLE', ?, now(), ?)", reqId, phId, prix, pharmacienProprietaire);
    }

    UUID insererReservation(UUID reqId, UUID phId, UUID patId, String code, String statut, int delaiSecondes, BigDecimal prix) {
        return jdbc.queryForObject("""
            INSERT INTO reservations (request_id, pharmacy_id, patient_id, pickup_code, status, price, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, now() + (?::integer * interval '1 second'))
            RETURNING id
            """, UUID.class, reqId, phId, patId, code, statut, prix, delaiSecondes);
    }

    String token(UUID user, String role) {
        return "Bearer " + Jwts.builder()
                .subject(user.toString())
                .claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)))
                .compact();
    }

    // =========================================================================
    // 1. RECHERCHE PAR CODE (GET /api/pharmacies/{pharmacy}/reservations/{code})
    // =========================================================================

    @Test
    void rechercheReussie() throws Exception {
        mvc.perform(get("/api/pharmacies/{pharmacy}/reservations/{code}", maPharmacie, codeActif)
                .header("Authorization", token(pharmacienProprietaire, "PHARMACIEN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reservationId.toString()))
                .andExpect(jsonPath("$.code").value(codeActif))
                .andExpect(jsonPath("$.price").value(85.50))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.medications", hasSize(2)))
                .andExpect(jsonPath("$.medications[0].name").value("Doliprane 1000mg"))
                .andExpect(jsonPath("$.medications[0].quantity").value(2))
                .andExpect(jsonPath("$.medications[1].name").value("Amoxicilline 500mg"))
                .andExpect(jsonPath("$.medications[1].quantity").value(1))
                // Loi 09-08 : pas d'identité ni patient_id renvoyé
                .andExpect(jsonPath("$.patientId").doesNotExist())
                .andExpect(jsonPath("$.patient_id").doesNotExist());
    }

    @Test
    void codeEnMinusculesAccepte() throws Exception {
        mvc.perform(get("/api/pharmacies/{pharmacy}/reservations/{code}", maPharmacie, codeActif.toLowerCase())
                .header("Authorization", token(pharmacienProprietaire, "PHARMACIEN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reservationId.toString()))
                .andExpect(jsonPath("$.code").value(codeActif));
    }

    @Test
    void codeInconnu_retourne404() throws Exception {
        mvc.perform(get("/api/pharmacies/{pharmacy}/reservations/{code}", maPharmacie, "ZZZ999")
                .header("Authorization", token(pharmacienProprietaire, "PHARMACIEN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Réservation introuvable ou expirée"));
    }

    @Test
    void codeExpire_retourne404() throws Exception {
        UUID demandeExpiree = creerDemande(patient);
        repondreDisponible(demandeExpiree, maPharmacie, new BigDecimal("85.50"));
        String codeExpire = "EXP999";
        insererReservation(demandeExpiree, maPharmacie, patient, codeExpire, "ACTIVE", -60, new BigDecimal("85.50"));

        mvc.perform(get("/api/pharmacies/{pharmacy}/reservations/{code}", maPharmacie, codeExpire)
                .header("Authorization", token(pharmacienProprietaire, "PHARMACIEN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Réservation introuvable ou expirée"));
    }

    @Test
    void codeDuneAutrePharmacie_retourne404() throws Exception {
        UUID demandeAutre = creerDemande(patient);
        repondreDisponible(demandeAutre, autrePharmacie, new BigDecimal("90.00"));
        String codeAutrePharmacie = "AUT234";
        insererReservation(demandeAutre, autrePharmacie, patient, codeAutrePharmacie, "ACTIVE", 3600, new BigDecimal("90.00"));

        // Le pharmacien propriétaire cherche le code de l'autre pharmacie dans SA pharmacie -> 404
        mvc.perform(get("/api/pharmacies/{pharmacy}/reservations/{code}", maPharmacie, codeAutrePharmacie)
                .header("Authorization", token(pharmacienProprietaire, "PHARMACIEN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Réservation introuvable ou expirée"));
    }

    @Test
    void pharmacieDunAutreProprietaire_retourne404() throws Exception {
        // Un pharmacien tente d'interroger la pharmacie d'un confrère -> 404
        mvc.perform(get("/api/pharmacies/{pharmacy}/reservations/{code}", maPharmacie, codeActif)
                .header("Authorization", token(autrePharmacien, "PHARMACIEN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Réservation introuvable ou expirée"));
    }

    @Test
    void formatInvalide_retourne400() throws Exception {
        // Moins de 6 caractères
        mvc.perform(get("/api/pharmacies/{pharmacy}/reservations/{code}", maPharmacie, "ABC")
                .header("Authorization", token(pharmacienProprietaire, "PHARMACIEN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Format de code de retrait invalide")));

        // Caractères ambigus exclus de l'alphabet (0, O, 1, I)
        mvc.perform(get("/api/pharmacies/{pharmacy}/reservations/{code}", maPharmacie, "AB01IO")
                .header("Authorization", token(pharmacienProprietaire, "PHARMACIEN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Format de code de retrait invalide")));
    }

    @Test
    void securite_sansJeton_retourne401() throws Exception {
        mvc.perform(get("/api/pharmacies/{pharmacy}/reservations/{code}", maPharmacie, codeActif))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void securite_rolePatient_retourne403() throws Exception {
        mvc.perform(get("/api/pharmacies/{pharmacy}/reservations/{code}", maPharmacie, codeActif)
                .header("Authorization", token(patient, "PATIENT")))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 2. VALIDATION DU RETRAIT (POST /api/pharmacies/{pharmacy}/reservations/{id}/pickup)
    // =========================================================================

    @Test
    void validationReussie_retireeEtDateRenseignee() throws Exception {
        mvc.perform(post("/api/pharmacies/{pharmacy}/reservations/{id}/pickup", maPharmacie, reservationId)
                .header("Authorization", token(pharmacienProprietaire, "PHARMACIEN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reservationId.toString()))
                .andExpect(jsonPath("$.status").value("RETIREE"))
                .andExpect(jsonPath("$.pickedUpAt").isNotEmpty());

        // Vérification directe en base
        String statutEnBase = jdbc.queryForObject("SELECT status FROM reservations WHERE id = ?", String.class, reservationId);
        assertThat(statutEnBase).isEqualTo("RETIREE");

        String pickedUpAt = jdbc.queryForObject("SELECT picked_up_at::text FROM reservations WHERE id = ?", String.class, reservationId);
        assertThat(pickedUpAt).isNotNull();
    }

    @Test
    void unCodeRetireNePeutPlusEtreCherche() throws Exception {
        // Valider le retrait
        mvc.perform(post("/api/pharmacies/{pharmacy}/reservations/{id}/pickup", maPharmacie, reservationId)
                .header("Authorization", token(pharmacienProprietaire, "PHARMACIEN")))
                .andExpect(status().isOk());

        // La recherche du même code retourne maintenant 404
        mvc.perform(get("/api/pharmacies/{pharmacy}/reservations/{code}", maPharmacie, codeActif)
                .header("Authorization", token(pharmacienProprietaire, "PHARMACIEN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Réservation introuvable ou expirée"));
    }

    @Test
    void doubleValidation_laSecondeRenvoie409() throws Exception {
        // Premier retrait : OK
        mvc.perform(post("/api/pharmacies/{pharmacy}/reservations/{id}/pickup", maPharmacie, reservationId)
                .header("Authorization", token(pharmacienProprietaire, "PHARMACIEN")))
                .andExpect(status().isOk());

        // Deuxième retrait : 409 Conflict
        mvc.perform(post("/api/pharmacies/{pharmacy}/reservations/{id}/pickup", maPharmacie, reservationId)
                .header("Authorization", token(pharmacienProprietaire, "PHARMACIEN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cette réservation a déjà été retirée"));
    }

    @Test
    void validationReservationExpiree_refuseeAvantPassageDuJob() throws Exception {
        UUID demandeExp = creerDemande(patient);
        repondreDisponible(demandeExp, maPharmacie, new BigDecimal("85.50"));
        String codeExp = "EXP777";
        // Réservation expirée il y a 10 secondes (status toujours ACTIVE en base)
        UUID resExpireeId = insererReservation(demandeExp, maPharmacie, patient, codeExp, "ACTIVE", -10, new BigDecimal("85.50"));

        mvc.perform(post("/api/pharmacies/{pharmacy}/reservations/{id}/pickup", maPharmacie, resExpireeId)
                .header("Authorization", token(pharmacienProprietaire, "PHARMACIEN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Réservation introuvable ou expirée"));

        // Le statut en base n'a pas été changé à RETIREE
        String statut = jdbc.queryForObject("SELECT status FROM reservations WHERE id = ?", String.class, resExpireeId);
        assertThat(statut).isEqualTo("ACTIVE");
    }

    @Test
    void validation_pharmacieDunAutreProprietaire_retourne404() throws Exception {
        mvc.perform(post("/api/pharmacies/{pharmacy}/reservations/{id}/pickup", maPharmacie, reservationId)
                .header("Authorization", token(autrePharmacien, "PHARMACIEN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDeConcurrence_deuxValidationsSimultanees_uneSeuleReussit() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var latch = new CountDownLatch(1);
        var succesCount = new AtomicInteger(0);
        var conflictCount = new AtomicInteger(0);

        Callable<Void> task = () -> {
            latch.await();
            var response = mvc.perform(post("/api/pharmacies/{pharmacy}/reservations/{id}/pickup", maPharmacie, reservationId)
                    .header("Authorization", token(pharmacienProprietaire, "PHARMACIEN")))
                    .andReturn().getResponse();

            if (response.getStatus() == 200) {
                succesCount.incrementAndGet();
            } else if (response.getStatus() == 409) {
                conflictCount.incrementAndGet();
            }
            return null;
        };

        Future<Void> f1 = executor.submit(task);
        Future<Void> f2 = executor.submit(task);

        // Déclencher les deux requêtes en parallèle exact
        latch.countDown();

        f1.get();
        f2.get();
        executor.shutdown();

        // Exactement 1 succès et 1 conflit
        assertThat(succesCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);
    }
}

