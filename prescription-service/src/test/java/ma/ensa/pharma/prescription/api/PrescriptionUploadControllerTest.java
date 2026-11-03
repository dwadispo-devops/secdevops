package ma.ensa.pharma.prescription.api;

import ma.ensa.pharma.prescription.domain.DetectedFileType;
import ma.ensa.pharma.prescription.exception.PrescriptionException;
import ma.ensa.pharma.prescription.security.PatientJwtService;
import ma.ensa.pharma.prescription.storage.Prescription;
import ma.ensa.pharma.prescription.storage.PrescriptionStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PrescriptionUploadController.class)
class PrescriptionUploadControllerTest {

    private static final String AUTH = "Bearer jeton";
    private static final MockMultipartFile FILE =
            new MockMultipartFile("file", "ordo.png", "image/png", new byte[]{1, 2, 3});

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PrescriptionStorageService storageService;

    @MockitoBean
    PatientJwtService jwtService;

    private final UUID patient = UUID.randomUUID();

    @Test
    void envoiOk201() throws Exception {
        Prescription saved = new Prescription(patient, DetectedFileType.PNG, 3);
        when(jwtService.authenticatedPatientId(AUTH)).thenReturn(patient);
        when(storageService.store(eq(patient), any(), eq("ordo.png"), eq(true))).thenReturn(saved);

        mvc.perform(multipart("/api/v1/prescriptions").file(FILE).param("consent", "true")
                        .header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/prescriptions/" + saved.getId()))
                .andExpect(jsonPath("$.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.fileType").value("PNG"));
    }

    @Test
    void sansConsentement400EtRienNEstEnregistre() throws Exception {
        when(jwtService.authenticatedPatientId(AUTH)).thenReturn(patient);

        mvc.perform(multipart("/api/v1/prescriptions").file(FILE)
                        .header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        verifyNoInteractions(storageService);
    }

    @Test
    void sansJeton401() throws Exception {
        when(jwtService.authenticatedPatientId(isNull())).thenThrow(PrescriptionException.unauthenticated());

        mvc.perform(multipart("/api/v1/prescriptions").file(FILE).param("consent", "true"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(storageService);
    }

    @Test
    void pharmacien403() throws Exception {
        when(jwtService.authenticatedPatientId(AUTH)).thenThrow(PrescriptionException.patientOnly());

        mvc.perform(multipart("/api/v1/prescriptions").file(FILE).param("consent", "true")
                        .header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(status().isForbidden());
    }

    @Test
    void fichierRefuse415() throws Exception {
        when(jwtService.authenticatedPatientId(AUTH)).thenReturn(patient);
        when(storageService.store(any(), any(), any(), anyBoolean())).thenThrow(PrescriptionException.unsupportedType());

        mvc.perform(multipart("/api/v1/prescriptions").file(FILE).param("consent", "true")
                        .header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    void sansFichier400() throws Exception {
        when(jwtService.authenticatedPatientId(AUTH)).thenReturn(patient);

        mvc.perform(multipart("/api/v1/prescriptions").param("consent", "true")
                        .header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_REQUIRED"));
    }

    @Test
    void imageRenvoyeeSansCache() throws Exception {
        UUID id = UUID.randomUUID();
        when(jwtService.authenticatedPatientId(AUTH)).thenReturn(patient);
        when(storageService.loadImage(patient, id))
                .thenReturn(new PrescriptionStorageService.StoredImage("image/png", new byte[]{9, 9}));

        mvc.perform(get("/api/v1/prescriptions/{id}/image", id).header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[]{9, 9}))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    @Test
    void imageDUnAutrePatient404() throws Exception {
        UUID id = UUID.randomUUID();
        when(jwtService.authenticatedPatientId(AUTH)).thenReturn(patient);
        when(storageService.loadImage(patient, id)).thenThrow(PrescriptionException.notFound());

        mvc.perform(get("/api/v1/prescriptions/{id}/image", id).header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(status().isNotFound());
    }

    @Test
    void suppression204() throws Exception {
        UUID id = UUID.randomUUID();
        when(jwtService.authenticatedPatientId(AUTH)).thenReturn(patient);

        mvc.perform(delete("/api/v1/prescriptions/{id}/image", id).header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(status().isNoContent());
        verify(storageService).deleteImage(patient, id);
    }
}
