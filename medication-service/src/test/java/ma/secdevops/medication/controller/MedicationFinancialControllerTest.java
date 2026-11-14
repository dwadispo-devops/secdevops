package ma.secdevops.medication.controller;

import ma.secdevops.medication.dto.MedicationFinancialResponse;
import ma.secdevops.medication.repository.MedicationFinancialRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@WebMvcTest(MedicationFinancialController.class)
class MedicationFinancialControllerTest {
    @Autowired MockMvc mvc;
    @MockBean MedicationFinancialRepository repository;
    @Test void exactBatchDeduplicatedAndNullsPreserved() throws Exception {
        when(repository.findByCodes(List.of("6118000000001"))).thenReturn(List.of(
            new MedicationFinancialResponse("6118000000001", "Test", "1 BOITE", new BigDecimal("12.85"), null)));
        mvc.perform(post("/api/medications/lookup").contentType(MediaType.APPLICATION_JSON)
            .content("{\"codes\":[\"6118000000001\",\"6118000000001\"]}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$[0].ppv").value(12.85))
            .andExpect(jsonPath("$[0].tauxRemboursement").value(nullValue()))
            .andExpect(jsonPath("$[0].prixBr").doesNotExist());
        verify(repository).findByCodes(List.of("6118000000001"));
    }
    @Test void invalidBatchIs400NotGlobal500() throws Exception {
        for (String body : List.of("{}", "{\"codes\":[]}", "{\"codes\":[null]}",
                "{\"codes\":[\"invalid\"]}", "{", "{\"codes\":[" + String.join(",", Collections.nCopies(101, "\"6118000000001\"")) + "]}")) {
            mvc.perform(post("/api/medications/lookup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(repository);
    }
}
