package ma.ensa.pharma.prescription.api;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import ma.ensa.pharma.prescription.catalog.FinancialCatalogClient;
import ma.ensa.pharma.prescription.security.PatientJwtService;
import ma.ensa.pharma.prescription.service.PrescriptionEstimateService;
import ma.ensa.pharma.prescription.storage.PrescriptionRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/** HTTP + validation + vrai JWT + vrai calcul ; catalogue et persistance isolés. */
@WebMvcTest(PrescriptionEstimateController.class)
@Import({PatientJwtService.class, PrescriptionEstimateService.class})
@TestPropertySource(properties = "app.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=")
class PrescriptionEstimateIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean FinancialCatalogClient catalog;
    @MockitoBean PrescriptionRepository repository;
    final String url = "/api/v1/prescriptions/estimate";
    final String body = "{\"items\":[{\"code\":\"6118000000001\",\"quantity\":2,\"unitConfirmed\":true,\"price\":0.01}]}";
    String token(String role, long delay) {
        return "Bearer " + Jwts.builder().subject(UUID.randomUUID().toString()).claim("role", role)
            .expiration(new Date(System.currentTimeMillis() + delay))
            .signWith(Keys.hmacShaKeyFor("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8))).compact();
    }
    @BeforeEach void setup() {
        when(catalog.lookup(List.of("6118000000001"))).thenReturn(List.of(new FinancialCatalogClient.Medication(
            "6118000000001", "Produit test", "1 BOITE", new BigDecimal("12.85"), 70)));
    }
    @Test void serverPricesAndNullReimbursementAndNoCache() throws Exception {
        mvc.perform(post(url).header("Authorization", token("PATIENT", 60000)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.totalEstimatedCost").value(25.7))
            .andExpect(jsonPath("$.reimbursableAmount").value(nullValue()))
            .andExpect(jsonPath("$.remainingCharge").value(nullValue()))
            .andExpect(jsonPath("$.lines[0].catalogRate").value(70));
    }
    @Test void absentExpiredForgedAndWrongRoleTokens() throws Exception {
        for (String auth : List.of("", token("PATIENT", -60000), "Bearer forged")) {
            mvc.perform(post(url).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        }
        mvc.perform(post(url).header("Authorization", token("PHARMACIEN", 60000)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        verifyNoInteractions(catalog);
    }
    @Test void invalidQuantitiesAndCodesAndNullLinesAre400() throws Exception {
        for (String invalid : List.of(body.replace("\"quantity\":2", "\"quantity\":0"),
                body.replace("\"quantity\":2", "\"quantity\":1.5"), body.replace("\"quantity\":2", "\"quantity\":10001"),
                body.replace("6118000000001", "invalid"), "{\"items\":[]}", "{\"items\":[null]}", "{}", "{")) {
            mvc.perform(post(url).header("Authorization", token("PATIENT", 60000)).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(catalog);
    }
    @Test void missingPrescriptionIs404AndCatalogNotCalled() throws Exception {
        mvc.perform(post(url).header("Authorization", token("PATIENT", 60000)).contentType(MediaType.APPLICATION_JSON)
            .content(body.replace("{\"items\"", "{\"prescriptionId\":\"" + UUID.randomUUID() + "\",\"items\"")))
            .andExpect(status().isNotFound());
        verifyNoInteractions(catalog);
    }
}
