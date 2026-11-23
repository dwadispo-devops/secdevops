package ma.secdevops.medication.generics;

import ma.secdevops.medication.generics.GenericsResponse.CheaperGeneric;
import ma.secdevops.medication.generics.GenericsResponse.Comparison;
import ma.secdevops.medication.generics.GenericsResponse.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GenericsController.class)
class GenericsControllerTest {

    private static final String AUGMENTIN = "6118000160182";

    @Autowired
    MockMvc mvc;

    @MockBean
    GenericsService service;

    @Test
    void returnsCheaperGenerics() throws Exception {
        Product ref = new Product(AUGMENTIN, "AUGMENTIN", "AMOXICILLINE / ACIDE CLAVULANIQUE", "1 / 125 G / MG",
                "POUDRE POUR SUSPENSION BUVABLE", "1 BOITE 16 SACHET", new BigDecimal("168.20"),
                new BigDecimal("10.51"), 70, "P");
        CheaperGeneric zamox = new CheaperGeneric("6118000041177", "ZAMOX", "1 / 125 G / MG",
                "POUDRE POUR SUSPENSION BUVABLE", "1 BOITE 24 SACHET", new BigDecimal("167.00"),
                new BigDecimal("6.96"), 33, Comparison.PAR_UNITE, 70, false);
        when(service.cheaperGenerics(AUGMENTIN, 10))
                .thenReturn(new GenericsResponse(ref, List.of(zamox), null, GenericsResponse.SOURCE));

        mvc.perform(get("/api/medications/{code}/generiques", AUGMENTIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference.nom").value("AUGMENTIN"))
                .andExpect(jsonPath("$.generiques[0].nom").value("ZAMOX"))
                .andExpect(jsonPath("$.generiques[0].prixUnitaire").value(6.96))
                .andExpect(jsonPath("$.generiques[0].economiePourcent").value(33))
                .andExpect(jsonPath("$.generiques[0].comparaison").value("PAR_UNITE"))
                .andExpect(jsonPath("$.source").value(GenericsResponse.SOURCE));
    }

    @Test
    void invalidCodeOrLimitIs400() throws Exception {
        for (String url : List.of("/api/medications/abc/generiques", "/api/medications/611800016018/generiques",
                "/api/medications/' OR 1=1 --/generiques")) {
            mvc.perform(get(url)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/medications/{code}/generiques", AUGMENTIN).param("limit", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/medications/{code}/generiques", AUGMENTIN).param("limit", "21"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/medications/{code}/generiques", AUGMENTIN).param("limit", "dix"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void unknownCodeIs404() throws Exception {
        when(service.cheaperGenerics(anyString(), anyInt())).thenThrow(new MedicationNotFoundException());

        mvc.perform(get("/api/medications/{code}/generiques", "6118999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Médicament introuvable dans le catalogue."));
    }
}
