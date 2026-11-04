package ma.ensa.pharma.prescription.api;

import ma.ensa.pharma.prescription.security.*;
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
    @MockitoBean StoredReadingService readings;
    @MockitoBean PrescriptionStorageService storage;
    UUID patient=UUID.randomUUID(), id=UUID.randomUUID();
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
}
