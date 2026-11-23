package ma.medidispo.pharmacy_service.admin;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PharmacyAdminIntegrationTest {
    private static final String TEST_SECRET = "c2VjcmV0LUEtc2VjcmV0LUEtc2VjcmV0LUEtc2VjcmV0LUEtMTIzNDU=";
    private static final SecretKey TEST_KEY = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
        DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void nettoyerDonneesUs21() {
        jdbc.update("DELETE FROM pharmacy_duties WHERE pharmacy_id IN (SELECT id FROM pharmacies WHERE name LIKE 'US21-%')");
        jdbc.update("DELETE FROM pharmacy_stock WHERE pharmacy_id IN (SELECT id FROM pharmacies WHERE name LIKE 'US21-%')");
        jdbc.update("DELETE FROM pharmacies WHERE name LIKE 'US21-%'");
    }

    private String jeton(UUID utilisateur, String role, long dureeSecondes) {
        return "Bearer " + Jwts.builder().subject(utilisateur.toString()).claim("role", role)
            .expiration(Date.from(Instant.now().plusSeconds(dureeSecondes))).signWith(TEST_KEY).compact();
    }

    private UUID pharmacie(String name, String status, String source, UUID owner, String license,
                           OffsetDateTime createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO pharmacies (id, name, address, latitude, longitude, status, source,
                                    owner_user_id, license_number, created_at)
            VALUES (?, ?, 'Adresse de test', 31.627, -7.988, ?, ?, ?, ?, ?)
            """, id, name, status, source, owner, license, createdAt);
        return id;
    }

    private UUID pharmacie(String name, String status, String source, UUID owner) {
        return pharmacie(name, status, source, owner,
            "US21-" + UUID.randomUUID(), OffsetDateTime.now());
    }

    @Test
    void listeSeulementLesPharmaciesEnAttenteEtLesChampsNecessaires() throws Exception {
        OffsetDateTime ancienne = OffsetDateTime.now().minusDays(2);
        pharmacie("US21-ancienne", "PENDING", "REGISTERED", UUID.randomUUID(), "US21-A", ancienne);
        pharmacie("US21-approuvee", "APPROVED", "REGISTERED", null);
        pharmacie("US21-osm", "UNVERIFIED", "OSM", null);
        pharmacie("US21-recente", "PENDING", "REGISTERED", UUID.randomUUID(), "US21-B", OffsetDateTime.now());

        mvc.perform(get("/api/pharmacies/admin/pending").header("Authorization", jeton(UUID.randomUUID(), "ADMIN", 60)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("US21-ancienne"))
            .andExpect(jsonPath("$[0].licenseNumber").value("US21-A"))
            .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
            .andExpect(jsonPath("$[0].id").isNotEmpty())
            .andExpect(jsonPath("$[0].ownerUserId").doesNotExist())
            .andExpect(jsonPath("$[0].email").doesNotExist())
            .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void valideUnePharmacieEtElleDevientVisibleAuxPatients() throws Exception {
        UUID id = pharmacie("US21-a-valider", "PENDING", "REGISTERED", UUID.randomUUID());
        String admin = jeton(UUID.randomUUID(), "ADMIN", 60);

        mvc.perform(get("/api/pharmacies/nearby").param("lat", "31.627").param("lng", "-7.988"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '%s')]".formatted(id)).doesNotExist());

        mvc.perform(post("/api/pharmacies/admin/{id}/validate", id).header("Authorization", admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.name").value("US21-a-valider"));
        assertThat(jdbc.queryForObject("SELECT status FROM pharmacies WHERE id = ?", String.class, id)).isEqualTo("APPROVED");

        mvc.perform(get("/api/pharmacies/nearby").param("lat", "31.627").param("lng", "-7.988"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '%s')]".formatted(id)).exists());
    }

    @Test
    void dejaApprouveeRenvoie409EtInconnue404() throws Exception {
        UUID approved = pharmacie("US21-deja-approuvee", "APPROVED", "REGISTERED", null);
        String admin = jeton(UUID.randomUUID(), "ADMIN", 60);
        mvc.perform(post("/api/pharmacies/admin/{id}/validate", approved).header("Authorization", admin))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(post("/api/pharmacies/admin/{id}/validate", UUID.randomUUID()).header("Authorization", admin))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void neValideJamaisUnePharmacieOsmUnverified() throws Exception {
        UUID osm = pharmacie("US21-osm", "UNVERIFIED", "OSM", null);
        mvc.perform(post("/api/pharmacies/admin/{id}/validate", osm)
                .header("Authorization", jeton(UUID.randomUUID(), "ADMIN", 60)))
            .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT status FROM pharmacies WHERE id = ?", String.class, osm)).isEqualTo("UNVERIFIED");
    }

    @Test
    void deuxValidationsSimultaneesOntUnSeulSucces() throws Exception {
        UUID id = pharmacie("US21-course", "PENDING", "REGISTERED", UUID.randomUUID());
        String admin = jeton(UUID.randomUUID(), "ADMIN", 60);
        var depart = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var premiere = pool.submit(() -> {
                depart.await();
                return mvc.perform(post("/api/pharmacies/admin/{id}/validate", id).header("Authorization", admin))
                    .andReturn().getResponse().getStatus();
            });
            var seconde = pool.submit(() -> {
                depart.await();
                return mvc.perform(post("/api/pharmacies/admin/{id}/validate", id).header("Authorization", admin))
                    .andReturn().getResponse().getStatus();
            });
            depart.countDown();
            assertThat(java.util.List.of(premiere.get(), seconde.get())).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM pharmacies WHERE id = ?", String.class, id)).isEqualTo("APPROVED");
    }

    @Test
    void validationNeModifiePasLesAutresPharmacies() throws Exception {
        UUID a = pharmacie("US21-a", "PENDING", "REGISTERED", UUID.randomUUID());
        UUID b = pharmacie("US21-b", "PENDING", "REGISTERED", UUID.randomUUID());
        mvc.perform(post("/api/pharmacies/admin/{id}/validate", a)
                .header("Authorization", jeton(UUID.randomUUID(), "ADMIN", 60))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM pharmacies WHERE id = ?", String.class, a)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT status FROM pharmacies WHERE id = ?", String.class, b)).isEqualTo("PENDING");
    }

    @Test
    void refuseLesRolesNonAdministrateursEtLesJetonsInvalides() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID id = pharmacie("US21-securite", "PENDING", "REGISTERED", owner);
        mvc.perform(get("/api/pharmacies/admin/pending")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/pharmacies/admin/{id}/validate", id)).andExpect(status().isUnauthorized());
        for (String role : java.util.List.of("PATIENT", "PHARMACIEN")) {
            mvc.perform(get("/api/pharmacies/admin/pending").header("Authorization", jeton(UUID.randomUUID(), role, 60)))
                .andExpect(status().isForbidden());
            mvc.perform(post("/api/pharmacies/admin/{id}/validate", id)
                    .header("Authorization", jeton(UUID.randomUUID(), role, 60))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/pharmacies/admin/{id}/validate", id)
                .header("Authorization", jeton(owner, "PHARMACIEN", 60))).andExpect(status().isForbidden());
        mvc.perform(get("/api/pharmacies/admin/pending").header("Authorization", "Bearer invalide"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pharmacies/admin/pending").header("Authorization", jeton(UUID.randomUUID(), "ADMIN", -60)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void identifiantMalFormeRenvoie400() throws Exception {
        mvc.perform(post("/api/pharmacies/admin/{id}/validate", "pas-un-uuid")
                .header("Authorization", jeton(UUID.randomUUID(), "ADMIN", 60)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void detailCacheLesPharmaciesNonVisiblesMaisResteAccessibleAuProprietaireEtAdmin() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID pending = pharmacie("US21-detail-pending", "PENDING", "REGISTERED", owner);
        UUID approved = pharmacie("US21-detail-approved", "APPROVED", "REGISTERED", UUID.randomUUID());
        UUID osm = pharmacie("US21-detail-osm", "UNVERIFIED", "OSM", null);

        mvc.perform(get("/api/pharmacies/{id}", pending).header("Authorization", jeton(UUID.randomUUID(), "PATIENT", 60)))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/pharmacies/{id}", pending).header("Authorization", jeton(owner, "PHARMACIEN", 60)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(get("/api/pharmacies/{id}", pending).header("Authorization", jeton(UUID.randomUUID(), "ADMIN", 60)))
            .andExpect(status().isOk());
        mvc.perform(get("/api/pharmacies/{id}", approved).header("Authorization", jeton(UUID.randomUUID(), "PATIENT", 60)))
            .andExpect(status().isOk());
        mvc.perform(get("/api/pharmacies/{id}", osm).header("Authorization", jeton(UUID.randomUUID(), "PATIENT", 60)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNVERIFIED"));
    }

    @Test
    void unePharmacieEnAttenteDeGardeNapparaitQuApresValidation() throws Exception {
        UUID id = pharmacie("US21-garde", "PENDING", "REGISTERED", UUID.randomUUID());
        LocalDate aujourdHui = LocalDate.now(java.time.ZoneId.of("Africa/Casablanca"));
        jdbc.update("INSERT INTO pharmacy_duties (pharmacy_id, starts_on, ends_on) VALUES (?, ?, ?)", id, aujourdHui, aujourdHui);

        mvc.perform(get("/api/pharmacies/garde")).andExpect(status().isOk())
            .andExpect(jsonPath("$.pharmacies[?(@.id == '%s')]".formatted(id)).doesNotExist());
        mvc.perform(post("/api/pharmacies/admin/{id}/validate", id)
                .header("Authorization", jeton(UUID.randomUUID(), "ADMIN", 60))).andExpect(status().isOk());
        mvc.perform(get("/api/pharmacies/garde")).andExpect(status().isOk())
            .andExpect(jsonPath("$.pharmacies[?(@.id == '%s')]".formatted(id)).exists());
    }

    @Test
    void uneRoutePatientVoisineGardeSesReglesDeSecurite() throws Exception {
        UUID pending = pharmacie("US21-voisine", "PENDING", "REGISTERED", UUID.randomUUID());
        mvc.perform(get("/api/pharmacies/{id}", pending).header("Authorization", jeton(UUID.randomUUID(), "PATIENT", 60)))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/pharmacies/me").header("Authorization", jeton(UUID.randomUUID(), "PATIENT", 60)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/pharmacies/me").header("Authorization", jeton(UUID.randomUUID(), "PHARMACIEN", 60)))
            .andExpect(status().isOk());
    }
}
