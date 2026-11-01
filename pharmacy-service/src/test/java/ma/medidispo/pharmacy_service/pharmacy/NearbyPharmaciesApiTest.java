package ma.medidispo.pharmacy_service.pharmacy;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US04 / EF15 : "En tant que patient, je veux voir les pharmacies proches de moi sur une carte".
 *
 * Test de contrat HTTP : on verifie ce que la carte recoit reellement.
 * - Base PostGIS jetable (Testcontainers), avec les vraies migrations Flyway.
 * - Aucun jeton envoye : la recherche doit etre publique.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class NearbyPharmaciesApiTest {

    // Position du patient (Marrakech)
    private static final String LAT = "31.6258";
    private static final String LNG = "-7.9891";

    // Base de donnees jetable : demarree avant les tests, detruite apres
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgis = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));

    @Autowired MockMvc mvc;          // simule des requetes HTTP sur le service
    @Autowired JdbcTemplate jdbc;    // permet d'ecrire des donnees de test en base

    // ETANT DONNE quatre pharmacies a des distances connues (refait avant chaque test)
    @BeforeEach
    void givenFourKnownPharmacies() {
        jdbc.update("DELETE FROM pharmacies");
        insert("A Proche", 31.6270, -7.9880, "APPROVED");      // environ 170 m
        insert("B Gueliz", 31.6300, -7.9811, "APPROVED");      // environ 890 m
        insert("C Lointaine", 31.7200, -7.9891, "APPROVED");   // environ 10,5 km
        insert("D Casablanca", 33.5731, -7.5898, "APPROVED");  // environ 230 km
    }

    private void insert(String name, double lat, double lng, String status) {
        jdbc.update("INSERT INTO pharmacies (name, latitude, longitude, status, source) VALUES (?, ?, ?, ?, 'TEST')",
                name, lat, lng, status);
    }

    // Appelle /nearby depuis la position du patient et renvoie le JSON recu
    private String search(Integer radius, Integer limit) throws Exception {
        var request = get("/api/pharmacies/nearby").param("lat", LAT).param("lng", LNG);
        if (radius != null) request.param("radius", radius.toString());
        if (limit != null) request.param("limit", limit.toString());
        return mvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // Extrait la liste des noms du JSON, dans l'ordre recu
    private static List<String> names(String json) {
        return JsonPath.read(json, "$[*].name");
    }

    @Test
    @DisplayName("US04-AC1 : rayon 5 km, on recoit A puis B dans cet ordre")
    void nearestFirstWithinRadius() throws Exception {
        assertThat(names(search(5000, null))).containsExactly("A Proche", "B Gueliz");
    }

    @Test
    @DisplayName("US04-AC2 : le rayon demande est respecte (500 m et 20 km)")
    void radiusIsRespected() throws Exception {
        assertThat(names(search(500, null))).containsExactly("A Proche");
        assertThat(names(search(20000, null))).containsExactly("A Proche", "B Gueliz", "C Lointaine");
    }

    @Test
    @DisplayName("US04-AC3 : limit=1 ne renvoie que la plus proche")
    void limitKeepsTheNearest() throws Exception {
        assertThat(names(search(20000, 1))).containsExactly("A Proche");
    }

    @Test
    @DisplayName("US04-AC4 : chaque element contient ce dont la carte a besoin")
    void eachResultHasWhatTheMapNeeds() throws Exception {
        mvc.perform(get("/api/pharmacies/nearby").param("lat", LAT).param("lng", LNG).param("radius", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].name").value("A Proche"))
                .andExpect(jsonPath("$[0].latitude").value(31.627))
                .andExpect(jsonPath("$[0].longitude").value(-7.988))
                .andExpect(jsonPath("$[0].distanceM").isNumber());
    }

    @Test
    @DisplayName("US04-AC5 : les distances renvoyees sont coherentes (en metres)")
    void distancesAreRealisticMeters() throws Exception {
        String json = search(5000, null);
        int a = JsonPath.read(json, "$[0].distanceM");
        int b = JsonPath.read(json, "$[1].distanceM");
        assertThat(a).isBetween(120, 220);
        assertThat(b).isBetween(820, 960);
        assertThat(a).isLessThan(b);
    }

    @Test
    @DisplayName("US04-AC7 : une pharmacie non approuvee n'apparait jamais")
    void pendingPharmacyIsHidden() throws Exception {
        insert("En attente", 31.6262, -7.9889, "PENDING");
        assertThat(names(search(5000, null))).doesNotContain("En attente");
    }

    @Test
    @DisplayName("US04-AC8 : aucune pharmacie dans la zone, 200 avec une liste vide")
    void emptyAreaReturnsEmptyList() throws Exception {
        mvc.perform(get("/api/pharmacies/nearby").param("lat", "0").param("lng", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("US04-AC9 : parametres invalides ou absents, 400")
    void invalidParametersAreRejected() throws Exception {
        for (String query : List.of(
                "lat=95&lng=-8", "lat=31.6&lng=181", "lat=31.6&lng=-8&radius=99",
                "lat=31.6&lng=-8&radius=20001", "lat=31.6&lng=-8&limit=0",
                "lat=31.6&lng=-8&limit=101", "lng=-8", "lat=abc&lng=-8")) {
            mvc.perform(get("/api/pharmacies/nearby?" + query))
                    .andExpect(status().isBadRequest());
        }
    }
}
