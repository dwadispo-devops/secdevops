package ma.ensa.pharma.prescription.ai;

import ma.ensa.pharma.prescription.api.dto.MedicationLineDto;
import ma.ensa.pharma.prescription.config.ReadingProperties;
import ma.ensa.pharma.prescription.domain.LineStatus;
import ma.ensa.pharma.prescription.domain.VisionExtraction;
import ma.ensa.pharma.prescription.domain.VisionExtraction.ExtractedMedication;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutputSanitizerTest {

    private final OutputSanitizer sanitizer = new OutputSanitizer(new ReadingProperties(2000, 40_000_000, 3));

    private static ExtractedMedication med(String raw, String name, boolean uncertain, Integer boxes) {
        return new ExtractedMedication(raw, name, "1 g", "comprimé", "1 cp x3/j", "5 jours", boxes, uncertain);
    }

    @Test
    void mapsStatusesAndNumbersLines() {
        VisionExtraction extraction = new VisionExtraction(true, List.of(
                med("Doliprane 1g", "Doliprane", false, 2),
                med("Augmentin 1g sachet", "Augmentin", true, null),
                med("~~~ illisible ~~~", "", true, null)));

        List<MedicationLineDto> lines = sanitizer.sanitize(extraction);

        assertThat(lines).extracting(MedicationLineDto::index).containsExactly(1, 2, 3);
        assertThat(lines).extracting(MedicationLineDto::status)
                .containsExactly(LineStatus.LU, LineStatus.INCERTAIN, LineStatus.ILLISIBLE);
        assertThat(lines.get(0).boxes()).isEqualTo(2);
    }

    @Test
    void dropsEmptyAndNullLines() {
        VisionExtraction extraction = new VisionExtraction(true,
                Arrays.asList(null, med("  ", null, false, null), med("Spasfon", "Spasfon", false, null)));

        List<MedicationLineDto> lines = sanitizer.sanitize(extraction);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).name()).isEqualTo("Spasfon");
        assertThat(lines.get(0).index()).isEqualTo(1);
    }

    @Test
    void limitsNumberOfLines() {
        List<ExtractedMedication> many = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            many.add(med("Med " + i, "Med " + i, false, null));
        }
        assertThat(sanitizer.sanitize(new VisionExtraction(true, many))).hasSize(3);
    }

    @Test
    void removesMarkupUrlsAndControlCharacters() {
        String malicious = "<script>alert(1)</script> Doliprane\u0000 http://evil.example/x {{ignore}}";

        String cleaned = OutputSanitizer.clean(malicious, 200);

        assertThat(cleaned).doesNotContain("<", ">", "{", "}", "http", "\u0000");
        assertThat(cleaned).contains("Doliprane");
    }

    @Test
    void truncatesLongFields() {
        String longName = "A".repeat(500);
        assertThat(OutputSanitizer.clean(longName, OutputSanitizer.MAX_FIELD_LENGTH))
                .hasSize(OutputSanitizer.MAX_FIELD_LENGTH);
    }

    @Test
    void ignoresAbsurdBoxCount() {
        VisionExtraction extraction = new VisionExtraction(true, List.of(
                med("Doliprane", "Doliprane", false, 999),
                med("Spasfon", "Spasfon", false, 0)));

        assertThat(sanitizer.sanitize(extraction)).extracting(MedicationLineDto::boxes).containsOnlyNulls();
    }

    @Test
    void handlesNullExtraction() {
        assertThat(sanitizer.sanitize(null)).isEmpty();
        assertThat(sanitizer.sanitize(new VisionExtraction(false, null))).isEmpty();
    }
}
