package ma.medidispo.pharmacy_service.pharmacy;

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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** US15 (amélioration) : le pharmacien déclare et supprime les gardes de ses pharmacies. */
@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class MyDutyIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
        DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Value("${app.jwt.secret}") String secret;

    static final LocalDate AUJOURDHUI = LocalDate.now(ZoneId.of("Africa/Casablanca"));
    UUID pharmacien, autrePharmacien, maPharmacie, autrePharmacie;
    String nom;

    @BeforeEach void donnees() {
        pharmacien = UUID.randomUUID(); autrePharmacien = UUID.randomUUID();
        nom = "Pharmacie garde " + UUID.randomUUID();
        maPharmacie = pharmacie(nom, pharmacien);
        autrePharmacie = pharmacie("Pharmacie voisine " + UUID.randomUUID(), autrePharmacien);
    }
    UUID pharmacie(String n, UUID owner) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO pharmacies (id,name,latitude,longitude,owner_user_id,status) VALUES (?,?,31.63,-8,?,'APPROVED')",
            id, n, owner);
        return id;
    }
    String token(UUID user, String role) {
        return "Bearer " + Jwts.builder().subject(user.toString()).claim("role", role)
            .expiration(new Date(System.currentTimeMillis() + 60000))
            .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))).compact();
    }
    ResultActions declarer(UUID ph, LocalDate debut, LocalDate fin, String auth) throws Exception {
        var r = post("/api/pharmacies/me/gardes").contentType(MediaType.APPLICATION_JSON)
            .content("{\"pharmacyId\":\"" + ph + "\",\"startsOn\":\"" + debut + "\",\"endsOn\":\"" + fin + "\"}");
        return mvc.perform(auth == null ? r : r.header("Authorization", auth));
    }

    @Test void lePharmacienDeclareUneGardeVisibleParLesPatients() throws Exception {
        declarer(maPharmacie, AUJOURDHUI, AUJOURDHUI.plusDays(2), token(pharmacien, "PHARMACIEN"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pharmacyName").value(nom))
            .andExpect(jsonPath("$.endsOn").value(AUJOURDHUI.plusDays(2).toString()));
        mvc.perform(get("/api/pharmacies/me/gardes").header("Authorization", token(pharmacien, "PHARMACIEN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].pharmacyName", hasItem(nom)));
        mvc.perform(get("/api/pharmacies/garde"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pharmacies[*].name", hasItem(nom)));
    }

    @Test void uneGardeQuiChevaucheEstRefusee() throws Exception {
        declarer(maPharmacie, AUJOURDHUI, AUJOURDHUI.plusDays(3), token(pharmacien, "PHARMACIEN")).andExpect(status().isCreated());
        declarer(maPharmacie, AUJOURDHUI.plusDays(2), AUJOURDHUI.plusDays(5), token(pharmacien, "PHARMACIEN"))
            .andExpect(status().isConflict());
    }

    @Test void lesDatesInvalidesSontRefusees() throws Exception {
        String auth = token(pharmacien, "PHARMACIEN");
        declarer(maPharmacie, AUJOURDHUI.plusDays(2), AUJOURDHUI, auth).andExpect(status().isBadRequest());
        declarer(maPharmacie, AUJOURDHUI.minusDays(5), AUJOURDHUI.minusDays(1), auth).andExpect(status().isBadRequest());
        declarer(maPharmacie, AUJOURDHUI, AUJOURDHUI.plusDays(31), auth).andExpect(status().isBadRequest());
    }

    @Test void onNeGerePasLaPharmacieDunAutre() throws Exception {
        declarer(autrePharmacie, AUJOURDHUI, AUJOURDHUI, token(pharmacien, "PHARMACIEN")).andExpect(status().isNotFound());
    }

    @Test void reserveAuPharmacienConnecte() throws Exception {
        declarer(maPharmacie, AUJOURDHUI, AUJOURDHUI, null).andExpect(status().isUnauthorized());
        declarer(maPharmacie, AUJOURDHUI, AUJOURDHUI, token(UUID.randomUUID(), "PATIENT")).andExpect(status().isForbidden());
    }

    @Test void seulLeProprietairePeutSupprimer() throws Exception {
        String id = com.jayway.jsonpath.JsonPath.read(
            declarer(maPharmacie, AUJOURDHUI, AUJOURDHUI, token(pharmacien, "PHARMACIEN"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(delete("/api/pharmacies/me/gardes/" + id).header("Authorization", token(autrePharmacien, "PHARMACIEN")))
            .andExpect(status().isNotFound());
        mvc.perform(delete("/api/pharmacies/me/gardes/" + id).header("Authorization", token(pharmacien, "PHARMACIEN")))
            .andExpect(status().isNoContent());
    }
}
