package ma.ensa.pharma.prescription.service;

import ma.ensa.pharma.prescription.TestImages;
import ma.ensa.pharma.prescription.ai.OutputSanitizer;
import ma.ensa.pharma.prescription.ai.VisionReader;
import ma.ensa.pharma.prescription.api.dto.LigneOrdonnanceDto;
import ma.ensa.pharma.prescription.api.dto.ReadPrescriptionResponse;
import ma.ensa.pharma.prescription.catalog.CatalogClient;
import ma.ensa.pharma.prescription.catalog.CatalogMatcher;
import ma.ensa.pharma.prescription.catalog.CatalogUnavailableException;
import ma.ensa.pharma.prescription.catalog.MatchScorer;
import ma.ensa.pharma.prescription.config.CatalogProperties;
import ma.ensa.pharma.prescription.config.ReadingProperties;
import ma.ensa.pharma.prescription.domain.VisionExtraction;
import ma.ensa.pharma.prescription.domain.VisionExtraction.ExtractedMedication;
import ma.ensa.pharma.prescription.exception.PrescriptionException;
import ma.ensa.pharma.prescription.file.FileValidator;
import ma.ensa.pharma.prescription.file.ImagePreprocessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.List;

import static ma.ensa.pharma.prescription.catalog.CatalogFixtures.DOLIPRANE_1000_CP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrescriptionReadingServiceTest {

    private VisionReader visionReader;
    private CatalogClient catalog;
    private PrescriptionReadingService service;

    @BeforeEach
    void setUp() {
        ReadingProperties properties = new ReadingProperties(2000, 40_000_000, 25);
        visionReader = mock(VisionReader.class);
        when(visionReader.providerName()).thenReturn("mock");
        catalog = mock(CatalogClient.class);
        CatalogMatcher matcher = new CatalogMatcher(catalog, new MatchScorer(),
                new CatalogProperties("http://localhost:8083", Duration.ofSeconds(3), 10));
        service = new PrescriptionReadingService(new FileValidator(), new ImagePreprocessor(properties),
                visionReader, new OutputSanitizer(properties), matcher);
    }

    private static ExtractedMedication med(String name, String dosage, String form, Integer quantity) {
        return new ExtractedMedication(name, dosage, form, quantity, false);
    }

    @Test
    void readsAndMatchesWithCatalog() {
        when(visionReader.read(any())).thenReturn(new VisionExtraction(true, List.of(
                med("DOLIPRANE", "1 g", "comprimé", 2))));
        when(catalog.search(anyString(), anyInt())).thenReturn(List.of(DOLIPRANE_1000_CP));

        ReadPrescriptionResponse response = service.read(TestImages.image("png", 600, 800), "ordo.png", true);

        assertThat(response.prescriptionDetected()).isTrue();
        assertThat(response.lignes()).hasSize(1);
        LigneOrdonnanceDto ligne = response.lignes().get(0);
        assertThat(ligne.texteLu()).isEqualTo("DOLIPRANE 1 g");
        assertThat(ligne.quantite()).isEqualTo(2);
        assertThat(ligne.score()).isGreaterThanOrEqualTo(0.85);
        assertThat(ligne.suggestions().get(0).id()).isEqualTo(DOLIPRANE_1000_CP.code());
        assertThat(response.provider()).isEqualTo("mock");
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
        assertThat(response.lignes()).isEmpty();
        assertThat(response.warnings()).anyMatch(w -> w.contains("ne semble pas"));
    }

    @Test
    void warnsOnIllegibleLineMissingQuantityAndMultiPagePdf() {
        when(visionReader.read(any())).thenReturn(new VisionExtraction(true, List.of(
                med("DOLIPRANE", "1 g", "comprimé", null),
                new ExtractedMedication("", "", "", 1, true))));
        when(catalog.search(anyString(), anyInt())).thenReturn(List.of(DOLIPRANE_1000_CP));

        ReadPrescriptionResponse response = service.read(TestImages.pdf(3), "ordo.pdf", true);

        assertThat(response.lignes()).hasSize(2);
        assertThat(response.lignes().get(1).texteLu()).isEqualTo("(illisible)");
        assertThat(response.warnings())
                .anyMatch(w -> w.contains("illisibles"))
                .anyMatch(w -> w.contains("quantité"))
                .anyMatch(w -> w.contains("première"));
    }

    @Test
    void stillAnswersWhenCatalogIsDown() {
        when(visionReader.read(any())).thenReturn(new VisionExtraction(true, List.of(
                med("DOLIPRANE", "1 g", "comprimé", 1))));
        when(catalog.search(anyString(), anyInt()))
                .thenThrow(new CatalogUnavailableException("down", new RuntimeException()));

        ReadPrescriptionResponse response = service.read(TestImages.image("png", 300, 300), "ordo.png", true);

        assertThat(response.lignes().get(0).score()).isZero();
        assertThat(response.warnings()).anyMatch(w -> w.contains("catalogue"));
    }

    @Test
    void propagatesModelUnavailability() {
        when(visionReader.read(any())).thenThrow(PrescriptionException.visionUnavailable(new RuntimeException()));

        assertThatThrownBy(() -> service.read(TestImages.image("png", 100, 100), "ordo.png", true))
                .isInstanceOf(PrescriptionException.class)
                .extracting("status").isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
