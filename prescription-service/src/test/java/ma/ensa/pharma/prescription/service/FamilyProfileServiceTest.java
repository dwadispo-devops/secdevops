package ma.ensa.pharma.prescription.service;

import ma.ensa.pharma.prescription.exception.PrescriptionException;
import ma.ensa.pharma.prescription.storage.FamilyProfile;
import ma.ensa.pharma.prescription.storage.FamilyProfileRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class FamilyProfileServiceTest {
    private final FamilyProfileRepository repository = mock(FamilyProfileRepository.class);
    private final FamilyProfileService service = new FamilyProfileService(repository);
    private final UUID patient = UUID.randomUUID();

    @Test void creeUnLibelleNormaliseSansStockerDeDonneesSupplementaires() {
        when(repository.existsByPatientIdAndDeletedAtIsNullAndLabelIgnoreCase(patient, "Maman")).thenReturn(false);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        FamilyProfile profile = service.create(patient, "  Maman  ");
        assertThat(profile.getLabel()).isEqualTo("Maman");
        assertThat(profile.getPatientId()).isEqualTo(patient);
        assertThat(profile.getDeletedAt()).isNull();
    }

    @Test void refuseUnLibelleEnDoubleEtUnLibelleVide() {
        when(repository.existsByPatientIdAndDeletedAtIsNullAndLabelIgnoreCase(patient, "Maman")).thenReturn(true);
        assertThatThrownBy(() -> service.create(patient, "Maman"))
                .isInstanceOf(PrescriptionException.class).hasMessage("Un profil porte déjà ce libellé.");
        assertThatThrownBy(() -> service.create(patient, "  "))
                .isInstanceOf(PrescriptionException.class).hasMessage("Le libellé doit contenir entre 1 et 60 caractères.");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test void renommeEtSupprimeLogiquementSeulementUnProfilActifDuPatient() {
        UUID id = UUID.randomUUID();
        FamilyProfile profile = new FamilyProfile(patient, "Maman");
        when(repository.findByIdAndPatientIdAndDeletedAtIsNull(id, patient)).thenReturn(Optional.of(profile));
        when(repository.existsByPatientIdAndDeletedAtIsNullAndLabelIgnoreCase(patient, "Mère")).thenReturn(false);
        when(repository.saveAndFlush(profile)).thenReturn(profile);
        assertThat(service.rename(patient, id, "Mère").getLabel()).isEqualTo("Mère");
        service.hide(patient, id);
        assertThat(profile.isActive()).isFalse();
    }
}
