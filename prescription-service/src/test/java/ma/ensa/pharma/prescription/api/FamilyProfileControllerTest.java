package ma.ensa.pharma.prescription.api;

import ma.ensa.pharma.prescription.exception.PrescriptionException;
import ma.ensa.pharma.prescription.security.PatientJwtService;
import ma.ensa.pharma.prescription.service.FamilyProfileService;
import ma.ensa.pharma.prescription.storage.FamilyProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FamilyProfileController.class)
class FamilyProfileControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean PatientJwtService patients;
    @MockitoBean FamilyProfileService profiles;
    private final UUID patient = UUID.randomUUID();

    @Test void creeUnProfilDepuisLePatientDuJeton() throws Exception {
        when(patients.authenticatedPatientId("Bearer patient")).thenReturn(patient);
        when(profiles.create(patient, "Maman")).thenReturn(new FamilyProfile(patient, "Maman"));
        mvc.perform(post("/api/v1/prescriptions/profiles").header("Authorization", "Bearer patient")
                        .contentType("application/json").content("{\"label\":\"Maman\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.label").value("Maman"))
                .andExpect(jsonPath("$.patientId").doesNotExist());
        verify(profiles).create(patient, "Maman");
    }

    @Test void listeSeulementLesProfilsDuPatientEtSansCache() throws Exception {
        when(patients.authenticatedPatientId("Bearer patient")).thenReturn(patient);
        when(profiles.list(patient)).thenReturn(List.of(new FamilyProfile(patient, "Yassine")));
        mvc.perform(get("/api/v1/prescriptions/profiles").header("Authorization", "Bearer patient"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].label").value("Yassine"));
    }

    @Test void modifiePuisMasqueUnProfil() throws Exception {
        UUID id = UUID.randomUUID();
        when(patients.authenticatedPatientId("Bearer patient")).thenReturn(patient);
        when(profiles.rename(patient, id, "Papa")).thenReturn(new FamilyProfile(patient, "Papa"));
        mvc.perform(patch("/api/v1/prescriptions/profiles/{id}", id).header("Authorization", "Bearer patient")
                        .contentType("application/json").content("{\"label\":\"Papa\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.label").value("Papa"));
        mvc.perform(delete("/api/v1/prescriptions/profiles/{id}", id).header("Authorization", "Bearer patient"))
                .andExpect(status().isNoContent());
        verify(profiles).hide(patient, id);
    }

    @Test void sessionAbsenteOuRoleNonPatientEstRefuse() throws Exception {
        when(patients.authenticatedPatientId(null)).thenThrow(PrescriptionException.unauthenticated());
        mvc.perform(get("/api/v1/prescriptions/profiles")).andExpect(status().isUnauthorized());
        when(patients.authenticatedPatientId("Bearer pharmacist")).thenThrow(PrescriptionException.patientOnly());
        mvc.perform(get("/api/v1/prescriptions/profiles").header("Authorization", "Bearer pharmacist"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(profiles);
    }
}
