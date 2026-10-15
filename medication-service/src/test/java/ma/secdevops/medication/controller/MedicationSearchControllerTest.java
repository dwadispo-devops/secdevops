package ma.secdevops.medication.controller;

import ma.secdevops.medication.dto.MedicationSearchResponse;
import ma.secdevops.medication.service.MedicationSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests unitaires du contrôleur MedicationSearchController avec MockMvc.
 */
@WebMvcTest(controllers = {MedicationSearchController.class, GlobalExceptionHandler.class})
class MedicationSearchControllerTest {

    @Test
    void compoundDosageIsReturnedWithoutChangingNumericField() throws Exception {
        var item = new MedicationSearchResponse("6118000000001", "QA COMPOSE", "QA",
                null, "MG / MG", "COMPRIME", "QA", null, 70, "G", "600 / 5");
        when(service.search(eq("compose"), anyInt())).thenReturn(List.of(item));
        mockMvc.perform(get("/api/medications/search").param("q", "compose"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dosage1").value(nullValue()))
                .andExpect(jsonPath("$[0].dosageBrut").value("600 / 5"))
                .andExpect(jsonPath("$[0].uniteDosage1").value("MG / MG"));
    }

    @Test
    void unknownRouteReturns404() throws Exception {
        mockMvc.perform(get("/api/medications/route-inexistante"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/medications/route-inexistante"));
    }

    @Test
    void wrongMethodsReturn405WithAllowHeader() throws Exception {
        for (var request : List.of(post("/api/medications/search"), put("/api/medications/search"))) {
            mockMvc.perform(request.param("q", "dolip"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(header().string("Allow", containsString("GET")))
                    .andExpect(jsonPath("$.status").value(405))
                    .andExpect(jsonPath("$.path").value("/api/medications/search"));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MedicationSearchService service;

    @Test
    @DisplayName("GET /api/medications/search sans paramètre 'q' -> 400 Bad Request")
    void testSearchMissingQuery() throws Exception {
        mockMvc.perform(get("/api/medications/search"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(containsString("obligatoire")))
                .andExpect(jsonPath("$.path").value("/api/medications/search"));
    }

    @Test
    @DisplayName("GET /api/medications/search avec 'q' trop court (<2 chars) -> 400 Bad Request")
    void testSearchQueryTooShort() throws Exception {
        mockMvc.perform(get("/api/medications/search").param("q", "a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("au moins 2 caractères")));

        // Avec espaces qui réduisent la taille après trim
        mockMvc.perform(get("/api/medications/search").param("q", " a "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("au moins 2 caractères")));
    }

    @Test
    @DisplayName("GET /api/medications/search avec 'q' trop long (>50 chars) -> 400 Bad Request")
    void testSearchQueryTooLong() throws Exception {
        String longQuery = "a".repeat(51);
        mockMvc.perform(get("/api/medications/search").param("q", longQuery))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("dépasser 50 caractères")));
    }

    @Test
    @DisplayName("GET /api/medications/search avec 'limit' < 1 -> 400 Bad Request")
    void testSearchLimitTooSmall() throws Exception {
        mockMvc.perform(get("/api/medications/search")
                        .param("q", "dolip")
                        .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("compris entre 1 et 20")));
    }

    @Test
    @DisplayName("GET /api/medications/search avec 'limit' > 20 -> 400 Bad Request")
    void testSearchLimitTooLarge() throws Exception {
        mockMvc.perform(get("/api/medications/search")
                        .param("q", "dolip")
                        .param("limit", "25"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("compris entre 1 et 20")));
    }

    @Test
    @DisplayName("GET /api/medications/search avec requête valide -> 200 OK et liste JSON")
    void testSearchSuccess() throws Exception {
        MedicationSearchResponse item = new MedicationSearchResponse(
                "6118000040958",
                "DOLIPRANE 1000 MG",
                "PARACETAMOL",
                new BigDecimal("1000.000"),
                "MG",
                "COMPRIME",
                "1 BOITE 8 COMPRIME",
                new BigDecimal("16.00"),
                70,
                "P",
                "1000"
        );

        when(service.search(eq("dolip"), anyInt())).thenReturn(List.of(item));

        mockMvc.perform(get("/api/medications/search")
                        .param("q", "dolip")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("6118000040958"))
                .andExpect(jsonPath("$[0].nom").value("DOLIPRANE 1000 MG"))
                .andExpect(jsonPath("$[0].dci1").value("PARACETAMOL"))
                .andExpect(jsonPath("$[0].dosage1").isNumber())
                .andExpect(jsonPath("$[0].dosage1").value(1000.0))
                .andExpect(jsonPath("$[0].dosageBrut").value("1000"))
                .andExpect(jsonPath("$[0].ppv").value(16.00))
                .andExpect(jsonPath("$[0].tauxRemboursement").value(70))
                .andExpect(jsonPath("$[0].princepsGenerique").value("P"));
    }

    @Test
    @DisplayName("GET /api/medications/search sans résultat -> 200 OK et tableau vide")
    void testSearchEmptyResult() throws Exception {
        when(service.search(eq("zzzzz"), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/medications/search").param("q", "zzzzz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
