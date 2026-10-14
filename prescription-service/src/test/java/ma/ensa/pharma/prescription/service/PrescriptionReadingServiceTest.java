package ma.ensa.pharma.prescription.service;

import ma.ensa.pharma.prescription.TestImages;
import ma.ensa.pharma.prescription.ai.OutputSanitizer;
import ma.ensa.pharma.prescription.ai.VisionReader;
import ma.ensa.pharma.prescription.api.dto.ReadPrescriptionResponse;
import ma.ensa.pharma.prescription.config.ReadingProperties;
import ma.ensa.pharma.prescription.domain.LineStatus;
import ma.ensa.pharma.prescription.domain.VisionExtraction;
import ma.ensa.pharma.prescription.domain.VisionExtraction.ExtractedMedication;
import ma.ensa.pharma.prescription.exception.PrescriptionException;
import ma.ensa.pharma.prescription.file.FileValidator;
import ma.ensa.pharma.prescription.file.ImagePreprocessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrescriptionReadingServiceTest {

    private VisionReader visionReader;
    private PrescriptionReadingService service;

    @BeforeEach
    void setUp() {
        ReadingProperties properties = new ReadingProperties(2000, 40_000_000, 25);
        visionReader = mock(VisionReader.class);
        when(visionReader.providerName()).thenReturn("mock");
        service = new PrescriptionReadingService(new FileValidator(), new ImagePreprocessor(properties),
                visionReader, new OutputSanitizer(properties));
    }

    @Test
    void readsPrescriptionEndToEndWithMockedModel() {
        when(visionReader.read(any())).thenReturn(new VisionExtraction(true, List.of(
                new ExtractedMedication("Doliprane 1g cp", "Doliprane", "1 g", "comprimé",
                        "1 cp 3 fois par jour", "5 jours", 1, false))));

        ReadPrescriptionResponse response = service.read(TestImages.image("png", 600, 800), "ordo.png", true);

        assertThat(response.prescriptionDetected()).isTrue();
        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).status()).isEqualTo(LineStatus.LU);
        assertThat(response.provider()).isEqualTo("mock");
        assertThat(response.readingId()).isNotNull();
        assertThat(response.warnings()).isEmpty();
    }

    @Test
    void refusesWithoutConsentAndNeverCallsTheModel() {
        assertThatThrownBy(() -> service.read(TestImages.image("png", 10, 10), "ordo.png", false))
                .isInstanceOf(PrescriptionException.class)
                .extracting("code").isEqualTo("CONSENT_REQUIRED");
        verify(visionReader, never()).read(any());
    }

    @Test
    void invalidFileNeverReachesTheModel() {
        assertThatThrownBy(() -> service.read("pas une image".getBytes(), "ordo.png", true))
                .isInstanceOf(PrescriptionException.class);
        verify(visionReader, never()).read(any());
    }

    @Test
    void warnsWhenImageIsNotAPrescription() {
        when(visionReader.read(any())).thenReturn(new VisionExtraction(false, List.of()));

        ReadPrescriptionResponse response = service.read(TestImages.image("jpg", 300, 300), "chat.jpg", true);

        assertThat(response.prescriptionDetected()).isFalse();
        assertThat(response.lines()).isEmpty();
        assertThat(response.warnings()).anyMatch(w -> w.contains("ne semble pas"));
    }

    @Test
    void warnsWhenNoMedicationRead() {
        when(visionReader.read(any())).thenReturn(new VisionExtraction(true, List.of()));

        ReadPrescriptionResponse response = service.read(TestImages.image("png", 300, 300), "ordo.png", true);

        assertThat(response.warnings()).anyMatch(w -> w.contains("saisissez vos médicaments à la main"));
    }

    @Test
    void warnsOnUncertainLinesAndMultiPagePdf() {
        when(visionReader.read(any())).thenReturn(new VisionExtraction(true, List.of(
                new ExtractedMedication("Augm... 1g", "Augmentin", "1 g", "", "", "", null, true))));

        ReadPrescriptionResponse response = service.read(TestImages.pdf(3), "ordo.pdf", true);

        assertThat(response.lines().get(0).status()).isEqualTo(LineStatus.INCERTAIN);
        assertThat(response.warnings())
                .anyMatch(w -> w.contains("incertaines"))
                .anyMatch(w -> w.contains("première"));
    }

    @Test
    void propagatesModelUnavailability() {
        when(visionReader.read(any())).thenThrow(PrescriptionException.visionUnavailable(new RuntimeException()));

        assertThatThrownBy(() -> service.read(TestImages.image("png", 100, 100), "ordo.png", true))
                .isInstanceOf(PrescriptionException.class)
                .extracting("status").isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
