package ma.ensa.pharma.prescription.api;

import ma.ensa.pharma.prescription.security.*;
import ma.ensa.pharma.prescription.api.dto.PrescriptionHistoryResponse;
import java.time.Instant;
import ma.ensa.pharma.prescription.service.StoredReadingService;
import ma.ensa.pharma.prescription.storage.PrescriptionStorageService;
import ma.ensa.pharma.prescription.exception.PrescriptionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StoredPrescriptionController.class)
class StoredPrescriptionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean PatientJwtService patients;
    @MockitoBean ReviewDelegationService delegation;
    @MockitoBean PurgeDelegationService purgeDelegation;
    @MockitoBean StoredReadingService readings;
    @MockitoBean PrescriptionStorageService storage;
    UUID patient=UUID.randomUUID(), id=UUID.randomUUID();
    @Test
    void patientHistory_returnsOnlyOwnPrescriptionsWithoutCache() throws Exception {
        UUID prescriptionId = UUID.randomUUID();
        when(patients.authenticatedPatientId("Bearer patient-token")).thenReturn(patient);
        when(readings.history(patient)).thenReturn(List.of(
            new PrescriptionHistoryResponse(prescriptionId, "UPLOADED", "PDF",
                Instant.parse("2026-11-13T10:00:00Z"), true)
        ));

        mvc.perform(get("/api/v1/prescriptions").header("Authorization", "Bearer patient-token"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$[0].id").value(prescriptionId.toString()))
            .andExpect(jsonPath("$[0].status").value("UPLOADED"))
            .andExpect(jsonPath("$[0].fileType").value("PDF"))
            .andExpect(jsonPath("$[0].imageAvailable").value(true))
            .andExpect(jsonPath("$[0].patientId").doesNotExist())
            .andExpect(jsonPath("$[0].readingData").doesNotExist());

        verify(readings).history(patient);
    }

    @Test
    void patientHistory_rejectsUnauthenticatedRequest() throws Exception {
        when(patients.authenticatedPatientId(null))
            .thenThrow(PrescriptionException.unauthenticated());

        mvc.perform(get("/api/v1/prescriptions"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(readings);
    }

    @Test void scopedImageUsesRealStorageAndNoCache() throws Exception {
        when(delegation.patient("Bearer server",id)).thenReturn(patient);
        when(storage.loadImage(patient,id)).thenReturn(new PrescriptionStorageService.StoredImage("image/png",new byte[]{1,2,3}));
        mvc.perform(get("/internal/prescriptions/"+id+"/image").header("Authorization","Bearer server"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(content().contentType("image/png")).andExpect(content().bytes(new byte[]{1,2,3}));
    }
    @Test void storedOcrViewReturnsExactText() throws Exception {
        when(delegation.patient("Bearer server",id)).thenReturn(patient);
        when(readings.view(patient,id)).thenReturn(new StoredReadingService.View(true,"image/png","Existing OCR",List.of()));
        mvc.perform(get("/internal/prescriptions/"+id).header("Authorization","Bearer server"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ocrText").value("Existing OCR"));
    }
    @Test void noDelegationAndDeletedImageFailClosed() throws Exception {
        when(delegation.patient(null,id)).thenThrow(PrescriptionException.unauthenticated());
        mvc.perform(get("/internal/prescriptions/"+id)).andExpect(status().isUnauthorized());
        verifyNoInteractions(readings,storage);
        when(delegation.patient("Bearer server",id)).thenReturn(patient);
        when(storage.loadImage(patient,id)).thenThrow(PrescriptionException.notFound());
        mvc.perform(get("/internal/prescriptions/"+id+"/image").header("Authorization","Bearer server"))
            .andExpect(status().isNotFound());
    }

    @Test
    void purgeImage_validToken_returns204AndCallsDelete() throws Exception {
        when(purgeDelegation.patient("Bearer purge-token", id)).thenReturn(patient);
        doNothing().when(storage).deleteImage(patient, id);

        mvc.perform(delete("/internal/prescriptions/" + id + "/image")
                .header("Authorization", "Bearer purge-token"))
                .andExpect(status().isNoContent());

        verify(storage).deleteImage(patient, id);
    }

    @Test
    void purgeImage_alreadyAbsentOrNonExistent_returns204Idempotent() throws Exception {
        when(purgeDelegation.patient("Bearer purge-token", id)).thenReturn(patient);
        doThrow(PrescriptionException.notFound()).when(storage).deleteImage(patient, id);

        mvc.perform(delete("/internal/prescriptions/" + id + "/image")
                .header("Authorization", "Bearer purge-token"))
                .andExpect(status().isNoContent());
    }

    @Test
    void purgeImage_invalidToken_returns401() throws Exception {
        when(purgeDelegation.patient(any(), eq(id))).thenThrow(PrescriptionException.unauthenticated());

        mvc.perform(delete("/internal/prescriptions/" + id + "/image")
                .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(storage);
    }
}
