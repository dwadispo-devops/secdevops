package ma.ensa.pharma.prescription.api;

import ma.ensa.pharma.prescription.api.dto.MedicationLineDto;
import ma.ensa.pharma.prescription.api.dto.ReadPrescriptionResponse;
import ma.ensa.pharma.prescription.domain.LineStatus;
import ma.ensa.pharma.prescription.exception.PrescriptionException;
import ma.ensa.pharma.prescription.service.PrescriptionReadingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PrescriptionController.class)
class PrescriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PrescriptionReadingService readingService;

    private final MockMultipartFile file =
            new MockMultipartFile("file", "ordo.png", "image/png", new byte[]{1, 2, 3});

    @Test
    void returnsReadLines() throws Exception {
        when(readingService.read(any(), eq("ordo.png"), eq(true))).thenReturn(new ReadPrescriptionResponse(
                UUID.randomUUID(), true,
                List.of(new MedicationLineDto(1, "Doliprane 1g", "Doliprane", "1 g", "comprimé",
                        "", "", null, LineStatus.LU)),
                "mock", 1200, List.of()));

        mockMvc.perform(multipart("/api/v1/prescriptions/read").file(file).param("consent", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prescriptionDetected").value(true))
                .andExpect(jsonPath("$.lines[0].name").value("Doliprane"))
                .andExpect(jsonPath("$.lines[0].status").value("LU"));
    }

    @Test
    void rejectsMissingConsent() throws Exception {
        mockMvc.perform(multipart("/api/v1/prescriptions/read").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        verify(readingService, never()).read(any(), anyString(), eq(true));
    }

    @Test
    void rejectsMissingFile() throws Exception {
        mockMvc.perform(multipart("/api/v1/prescriptions/read").param("consent", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_REQUIRED"));
    }

    @Test
    void mapsBusinessErrorsToProblemDetail() throws Exception {
        when(readingService.read(any(), anyString(), eq(true))).thenThrow(PrescriptionException.unsupportedType());

        mockMvc.perform(multipart("/api/v1/prescriptions/read").file(file).param("consent", "true"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    void returns503WhenModelIsDown() throws Exception {
        when(readingService.read(any(), anyString(), eq(true)))
                .thenThrow(PrescriptionException.visionUnavailable(new RuntimeException("timeout")));

        mockMvc.perform(multipart("/api/v1/prescriptions/read").file(file).param("consent", "true"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("VISION_UNAVAILABLE"));
    }
}
