package ma.medidispo.pharmacy_service.rating;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class PharmacyRatingIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
        DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Value("${app.jwt.secret}") String secret;
    UUID patient, pharmacy, request, reservation;

    @BeforeEach void setup() {
        patient = UUID.randomUUID(); pharmacy = UUID.randomUUID(); request = UUID.randomUUID(); reservation = UUID.randomUUID();
        jdbc.update("INSERT INTO pharmacies (id,name,latitude,longitude) VALUES (?,'Notation',31.63,-8)", pharmacy);
        jdbc.update("INSERT INTO availability_requests (id,patient_id,latitude,longitude,radius_m) VALUES (?,?,31.63,-8,5000)", request, patient);
        jdbc.update("INSERT INTO availability_request_pharmacies (request_id,pharmacy_id) VALUES (?,?)", request, pharmacy);
        jdbc.update("""
            INSERT INTO reservations (id,request_id,pharmacy_id,patient_id,pickup_code,status,expires_at,picked_up_at)
            VALUES (?,?,?,?,'ABC234','RETIREE',now() - interval '1 day',now())
            """, reservation, request, pharmacy, patient);
    }
    String url() { return "/api/availability-requests/" + request + "/reservations/" + reservation + "/rating"; }
    String token(UUID user, String role, long lifetime) {
        return "Bearer " + Jwts.builder().subject(user.toString()).claim("role", role)
            .expiration(new Date(System.currentTimeMillis() + lifetime))
            .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))).compact();
    }
    String auth() { return token(patient, "PATIENT", 60000); }
    ResultActions rate(String body) throws Exception {
        return mvc.perform(post(url()).header("Authorization", auth()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test void allStarsAndPostgresPersistenceAndMigration() throws Exception {
        for (int stars = 1; stars <= 5; stars++) {
            jdbc.update("DELETE FROM pharmacy_ratings WHERE reservation_id = ?", reservation);
            rate("{\"stars\":" + stars + "}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.stars").value(stars)).andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.reservationId").value(reservation.toString()))
                .andExpect(jsonPath("$.patientId").doesNotExist()).andExpect(jsonPath("$.pharmacyId").doesNotExist());
            mvc.perform(get(url()).header("Authorization", auth())).andExpect(status().isOk())
                .andExpect(jsonPath("$.stars").value(stars)).andExpect(header().string("Cache-Control", "no-store"));
            assertThat(jdbc.queryForObject("SELECT stars FROM pharmacy_ratings WHERE reservation_id = ?", Integer.class, reservation)).isEqualTo(stars);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version = '10' AND success", Integer.class)).isEqualTo(1);
    }
    @Test void noRatingIs204() throws Exception {
        mvc.perform(get(url()).header("Authorization", auth())).andExpect(status().isNoContent());
    }
    @Test void invalidStarsAre400() throws Exception {
        for (String body : List.of("{\"stars\":0}", "{\"stars\":6}", "{\"stars\":1.5}", "{\"stars\":1.0}", "{}",
                "{\"stars\":null}", "{\"stars\":\"3\"}", "{\"stars\":true}"))
            rate(body).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pharmacy_ratings WHERE reservation_id = ?", Integer.class, reservation)).isZero();
    }
    @Test void nonPickedUpStatusesAndMissingDateAre409() throws Exception {
        for (String state : List.of("ACTIVE", "ANNULEE", "EXPIREE")) {
            jdbc.update("UPDATE reservations SET status = ? WHERE id = ?", state, reservation);
            rate("{\"stars\":4}").andExpect(status().isConflict());
            mvc.perform(get(url()).header("Authorization", auth())).andExpect(status().isConflict());
        }
        jdbc.update("UPDATE reservations SET status = 'RETIREE', picked_up_at = NULL WHERE id = ?", reservation);
        rate("{\"stars\":4}").andExpect(status().isConflict());
    }
    @Test void otherPatientWrongRequestAndMissingReservationAre404() throws Exception {
        String actual = url();
        for (String path : List.of(actual, actual.replace(request.toString(), UUID.randomUUID().toString()),
                actual.replace(reservation.toString(), UUID.randomUUID().toString()))) {
            String authorization = path.equals(actual) ? token(UUID.randomUUID(), "PATIENT", 60000) : auth();
            mvc.perform(post(path).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content("{\"stars\":4}"))
                .andExpect(status().isNotFound());
            mvc.perform(get(path).header("Authorization", authorization)).andExpect(status().isNotFound());
        }
    }
    @Test void jwtAndRoles() throws Exception {
        for (String authorization : List.of("", "Bearer forged", token(patient, "PATIENT", -60000))) {
            mvc.perform(post(url()).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content("{\"stars\":4}"))
                .andExpect(status().isUnauthorized());
            mvc.perform(get(url()).header("Authorization", authorization)).andExpect(status().isUnauthorized());
        }
        for (String role : List.of("PHARMACIEN", "ADMIN")) {
            mvc.perform(post(url()).header("Authorization", token(patient, role, 60000)).contentType(MediaType.APPLICATION_JSON).content("{\"stars\":4}"))
                .andExpect(status().isForbidden());
            mvc.perform(get(url()).header("Authorization", token(patient, role, 60000))).andExpect(status().isForbidden());
        }
    }
    @Test void doubleRatingIs409AndOriginalRemains() throws Exception {
        rate("{\"stars\":2}").andExpect(status().isCreated());
        rate("{\"stars\":5}").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT stars FROM pharmacy_ratings WHERE reservation_id = ?", Integer.class, reservation)).isEqualTo(2);
    }
    @Test void concurrentSubmissionsCreateExactlyOneRating() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> task = () -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new TimeoutException();
                return rate("{\"stars\":4}").andReturn().getResponse().getStatus();
            };
            var first = executor.submit(task); var second = executor.submit(task);
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pharmacy_ratings WHERE reservation_id = ?", Integer.class, reservation)).isEqualTo(1);
    }
    @Test void databaseConstraintsProtectAgainstDirectInvalidWrites() {
        jdbc.update("INSERT INTO pharmacy_ratings (reservation_id,stars) VALUES (?,3)", reservation);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO pharmacy_ratings (reservation_id,stars) VALUES (?,4)", reservation))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update("DELETE FROM pharmacy_ratings WHERE reservation_id = ?", reservation);
        for (int stars : List.of(0, 6))
            assertThatThrownBy(() -> jdbc.update("INSERT INTO pharmacy_ratings (reservation_id,stars) VALUES (?,?)", reservation, stars))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO pharmacy_ratings (reservation_id,stars) VALUES (?,3)", UUID.randomUUID()))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
