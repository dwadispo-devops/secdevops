package ma.medidispo.pharmacy_service.review;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class ReviewIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Value("${app.jwt.secret}") String secret;
    @MockitoBean PrescriptionClient prescriptions;
    UUID owner, patient, pharmacy, request, prescription;
    String base;
    @BeforeEach void data() {
        owner=UUID.randomUUID(); patient=UUID.randomUUID(); pharmacy=UUID.randomUUID(); request=UUID.randomUUID(); prescription=UUID.randomUUID();
        jdbc.update("INSERT INTO pharmacies (id,name,latitude,longitude,owner_user_id) VALUES (?,'US10 synthetic',31.63,-8,?)",pharmacy,owner);
        jdbc.update("INSERT INTO availability_requests (id,patient_id,latitude,longitude,radius_m,prescription_id) VALUES (?,?,31.63,-8,5000,?)",request,patient,prescription);
        jdbc.update("INSERT INTO availability_request_pharmacies (request_id,pharmacy_id) VALUES (?,?)",request,pharmacy);
        jdbc.update("INSERT INTO availability_request_items (request_id,medication_name,quantity) VALUES (?,'Synthetic medication',2)",request);
        base="/api/pharmacies/"+pharmacy+"/requests/"+request;
    }
    String token(UUID user,String role) {
        return "Bearer "+Jwts.builder().subject(user.toString()).claim("role",role)
            .expiration(new Date(System.currentTimeMillis()+60000)).signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))).compact();
    }
    @Test void inboxContainsOnlyOwnedRequests() throws Exception {
        mvc.perform(get("/api/pharmacies/me/requests").header("Authorization",token(owner,"PHARMACIEN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].requestId").value(request.toString()));
        mvc.perform(get("/api/pharmacies/me/requests").header("Authorization",token(UUID.randomUUID(),"PHARMACIEN")))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
    }
    @Test void assignedDetailContainsPatientItems() throws Exception {
        mvc.perform(get(base).header("Authorization",token(owner,"PHARMACIEN"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.request.status").value("PENDING"))
            .andExpect(jsonPath("$.medications[0].quantity").value(2));
    }
    @Test void absentAndForeignRequestsAre404WithoutCallingPrescription() throws Exception {
        mvc.perform(get(base).header("Authorization",token(UUID.randomUUID(),"PHARMACIEN"))).andExpect(status().isNotFound());
        mvc.perform(get(base.replace(request.toString(),UUID.randomUUID().toString())).header("Authorization",token(owner,"PHARMACIEN"))).andExpect(status().isNotFound());
        mvc.perform(get(base+"/prescription/image").header("Authorization",token(UUID.randomUUID(),"PHARMACIEN"))).andExpect(status().isNotFound());
        verifyNoInteractions(prescriptions);
    }
    @Test void missingJwt401AndPatient403() throws Exception {
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        mvc.perform(get(base).header("Authorization",token(patient,"PATIENT"))).andExpect(status().isForbidden());
        mvc.perform(post(base+"/response").contentType(MediaType.APPLICATION_JSON).content("{\"availability\":\"UNAVAILABLE\"}"))
            .andExpect(status().isUnauthorized());
    }
    @Test void authenticImageAndOcrAreProxiedWithoutCaching() throws Exception {
        when(prescriptions.details(patient,prescription)).thenReturn(new ReviewDtos.Prescription(true,"image/png","Actual OCR test text",List.of()));
        when(prescriptions.image(patient,prescription)).thenReturn(ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(new byte[]{1,2,3}));
        mvc.perform(get(base+"/prescription").header("Authorization",token(owner,"PHARMACIEN"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.ocrText").value("Actual OCR test text"));
        mvc.perform(get(base+"/prescription/image").header("Authorization",token(owner,"PHARMACIEN")))
            .andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1,2,3})).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void missingPrescriptionIsExplicitAndServiceFailure503() throws Exception {
        when(prescriptions.details(patient,prescription)).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
        mvc.perform(get(base+"/prescription").header("Authorization",token(owner,"PHARMACIEN"))).andExpect(status().isServiceUnavailable());
        jdbc.update("UPDATE availability_requests SET prescription_id=NULL WHERE id=?",request);
        mvc.perform(get(base+"/prescription").header("Authorization",token(owner,"PHARMACIEN"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.imageAvailable").value(false)).andExpect(jsonPath("$.ocrText").doesNotExist());
        mvc.perform(get(base+"/prescription/image").header("Authorization",token(owner,"PHARMACIEN"))).andExpect(status().isNotFound());
    }
    @ParameterizedTest @ValueSource(strings={"0","12.50","9999999999.99"})
    void availableWithValidPricePersistsAndPatientCanRead(String price) throws Exception {
        mvc.perform(post(base+"/response").header("Authorization",token(owner,"PHARMACIEN"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"availability\":\"AVAILABLE\",\"price\":"+price+",\"comment\":\"Ready\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AVAILABLE"));
        assertThat(jdbc.queryForObject("SELECT responded_by FROM availability_request_pharmacies WHERE request_id=?",UUID.class,request)).isEqualTo(owner);
        mvc.perform(get("/api/availability-requests/"+request+"/responses").header("Authorization",token(patient,"PATIENT")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].comment").value("Ready"));
        mvc.perform(get("/api/availability-requests/"+request+"/responses").header("Authorization",token(UUID.randomUUID(),"PATIENT")))
            .andExpect(status().isNotFound());
    }
    @Test void unavailableAndRepeatedResponse409() throws Exception {
        for (int i=0;i<2;i++) mvc.perform(post(base+"/response").header("Authorization",token(owner,"PHARMACIEN"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"availability\":\"UNAVAILABLE\"}"))
            .andExpect(i==0?status().isOk():status().isConflict());
        assertThat(jdbc.queryForObject("SELECT status FROM availability_request_pharmacies WHERE request_id=?",String.class,request)).isEqualTo("UNAVAILABLE");
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"availability\":\"UNKNOWN\"}","{\"availability\":\"AVAILABLE\"}",
        "{\"availability\":\"AVAILABLE\",\"price\":-1}","{\"availability\":\"AVAILABLE\",\"price\":1.234}",
        "{\"availability\":\"UNAVAILABLE\",\"price\":0}","{\"availability\":\"AVAILABLE\",\"price\":10000000000}"})
    void invalidResponseDoesNotWrite(String body) throws Exception {
        mvc.perform(post(base+"/response").header("Authorization",token(owner,"PHARMACIEN"))
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT status FROM availability_request_pharmacies WHERE request_id=?",String.class,request)).isEqualTo("PENDING");
    }
    @Test void foreignPharmacyCannotRespondOrSpoofOwner() throws Exception {
        mvc.perform(post(base+"/response").header("Authorization",token(UUID.randomUUID(),"PHARMACIEN"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"availability\":\"UNAVAILABLE\",\"ownerId\":\""+owner+"\"}"))
            .andExpect(status().isNotFound());
    }
    @Test void attachingChecksPatientAndUpstreamOwnership() throws Exception {
        jdbc.update("UPDATE availability_requests SET prescription_id=NULL WHERE id=?",request);
        var url="/api/availability-requests/"+request+"/prescription";
        var body="{\"prescriptionId\":\""+prescription+"\"}";
        mvc.perform(put(url).header("Authorization",token(UUID.randomUUID(),"PATIENT")).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNotFound());
        verifyNoInteractions(prescriptions);
        when(prescriptions.details(patient,prescription)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        mvc.perform(put(url).header("Authorization",token(patient,"PATIENT")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        doReturn(new ReviewDtos.Prescription(true,"image/png",null,List.of())).when(prescriptions).details(patient,prescription);
        mvc.perform(put(url).header("Authorization",token(patient,"PATIENT")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        mvc.perform(put(url).header("Authorization",token(patient,"PATIENT")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
    }
    @Test void concurrentResponsesHaveExactlyOneWinner() throws Exception {
        try (var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> action=() -> {
                start.await();
                return mvc.perform(post(base+"/response").header("Authorization",token(owner,"PHARMACIEN"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"availability\":\"UNAVAILABLE\"}"))
                    .andReturn().getResponse().getStatus();
            };
            var a=pool.submit(action); var b=pool.submit(action); start.countDown();
            assertThat(List.of(a.get(),b.get())).containsExactlyInAnyOrder(200,409);
        }
    }
}
