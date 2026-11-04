package ma.ensa.pharma.prescription.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.ensa.pharma.prescription.api.dto.*;
import ma.ensa.pharma.prescription.domain.*;
import ma.ensa.pharma.prescription.exception.PrescriptionException;
import ma.ensa.pharma.prescription.storage.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class StoredReadingServiceTest {
    private final PrescriptionRepository repository=mock(PrescriptionRepository.class);
    private final PrescriptionStorageService storage=mock(PrescriptionStorageService.class);
    private final PrescriptionReadingService reader=mock(PrescriptionReadingService.class);
    private final ImageEncryptionService encryption=new ImageEncryptionService(Base64.getEncoder().encodeToString(new byte[32]));
    private final ObjectMapper mapper=new ObjectMapper();
    private final UUID patient=UUID.randomUUID();
    private Prescription prescription;
    private StoredReadingService service;
    @BeforeEach void setup() {
        prescription=new Prescription(patient,DetectedFileType.PNG,3);
        when(repository.findById(prescription.getId())).thenReturn(Optional.of(prescription));
        when(repository.lockById(prescription.getId())).thenReturn(Optional.of(prescription));
        when(storage.loadImage(patient,prescription.getId())).thenReturn(new PrescriptionStorageService.StoredImage("image/png",new byte[]{1,2,3}));
        service=new StoredReadingService(repository,storage,reader,encryption,mapper);
    }
    @Test void noReadingMeansExplicitNullNotInventedOcr() {
        var result=service.view(patient,prescription.getId());
        assertThat(result.imageAvailable()).isTrue(); assertThat(result.ocrText()).isNull(); assertThat(result.medications()).isEmpty();
        verifyNoInteractions(reader);
    }
    @Test void storesRealReaderOutputEncryptedAndReturnsItsExactText() {
        var line=new MedicationLineDto(1,"Synthetic OCR text","Synthetic","1 g",null,null,null,1,LineStatus.LU);
        when(reader.read(any(),eq("prescription.png"),eq(true))).thenReturn(new ReadPrescriptionResponse(UUID.randomUUID(),true,List.of(line),"test",1,List.of()));
        service.read(patient,prescription.getId(),true);
        assertThat(new String(prescription.getReadingData(),java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("Synthetic OCR text");
        var result=service.view(patient,prescription.getId());
        assertThat(result.ocrText()).isEqualTo("Synthetic OCR text"); assertThat(result.medications().getFirst().name()).isEqualTo("Synthetic");
        prescription.markImageDeleted();
        assertThat(prescription.getReadingData()).isNull(); assertThat(service.view(patient,prescription.getId()).imageAvailable()).isFalse();
    }
    @Test void unavailableModelNeverPersistsFakeReading() {
        when(reader.read(any(),anyString(),eq(true))).thenThrow(PrescriptionException.visionUnavailable(new RuntimeException()));
        assertThatThrownBy(() -> service.read(patient,prescription.getId(),true)).isInstanceOf(PrescriptionException.class);
        verify(repository,never()).save(any()); assertThat(prescription.getReadingData()).isNull();
    }
    @Test void rejectsOtherPatientMissingPrescriptionAndNoConsent() {
        assertThatThrownBy(() -> service.view(UUID.randomUUID(),prescription.getId())).isInstanceOf(PrescriptionException.class);
        assertThatThrownBy(() -> service.view(patient,UUID.randomUUID())).isInstanceOf(PrescriptionException.class);
        assertThatThrownBy(() -> service.read(patient,prescription.getId(),false)).isInstanceOf(PrescriptionException.class);
        assertThatThrownBy(() -> service.read(UUID.randomUUID(),prescription.getId(),true)).isInstanceOf(PrescriptionException.class);
        verifyNoInteractions(reader);
    }
}
