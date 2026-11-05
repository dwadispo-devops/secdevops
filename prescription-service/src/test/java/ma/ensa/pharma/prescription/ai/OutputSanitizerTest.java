package ma.ensa.pharma.prescription.ai;

import ma.ensa.pharma.prescription.config.ReadingProperties;
import ma.ensa.pharma.prescription.domain.ReadLine;
import ma.ensa.pharma.prescription.domain.VisionExtraction;
import ma.ensa.pharma.prescription.domain.VisionExtraction.ExtractedMedication;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutputSanitizerTest {

    private final OutputSanitizer sanitizer = new OutputSanitizer(new ReadingProperties(2000, 40_000_000, 3));

    private static ExtractedMedication med(String name, String dosage, Integer quantity, boolean uncertain) {
        return new ExtractedMedication(name, dosage, "comprimé", quantity, uncertain);
    }

    @Test
    void numbersLinesAndKeepsFields() {
        List<ReadLine> lines = sanitizer.sanitize(new VisionExtraction(true, List.of(
                med("DOLIPRANE", "1 g", 1, false),
                med("AUGMENTIN", "1 g/125 mg", 2, true))));

        assertThat(lines).containsExactly(
                new ReadLine(1, "DOLIPRANE", "1 g", "comprimé", 1, false),
                new ReadLine(2, "AUGMENTIN", "1 g/125 mg", "comprimé", 2, true));
        assertThat(lines.get(0).readText()).isEqualTo("DOLIPRANE 1 g");
    }

    @Test
    void keepsIllegibleLinesForManualEntry() {
        List<ReadLine> lines = sanitizer.sanitize(new VisionExtraction(true, List.of(med("", "", null, true))));

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).illegible()).isTrue();
        assertThat(lines.get(0).readText()).isEqualTo("(illisible)");
    }

    @Test
    void dropsNullAndCompletelyEmptyLines() {
        List<ReadLine> lines = sanitizer.sanitize(new VisionExtraction(true, Arrays.asList(
                null,
                new ExtractedMedication(" ", null, null, null, false),
                med("SPASFON", "80 mg", 1, false))));

        assertThat(lines).extracting(ReadLine::name).containsExactly("SPASFON");
        assertThat(lines.get(0).id()).isEqualTo(1);
    }

    @Test
    void limitsNumberOfLines() {
        List<ExtractedMedication> many = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            many.add(med("Med " + i, "", 1, false));
        }
        assertThat(sanitizer.sanitize(new VisionExtraction(true, many))).hasSize(3);
    }

    @Test
    void removesMarkupUrlsAndControlCharacters() {
        String malicious = "<script>alert(1)</script> Doliprane\u0000 http://evil.example/x {{ignore}}";

        String cleaned = OutputSanitizer.clean(malicious);

        assertThat(cleaned).doesNotContain("<", ">", "{", "}", "http", "\u0000");
        assertThat(cleaned).contains("Doliprane");
    }

    @Test
    void truncatesLongFields() {
        assertThat(OutputSanitizer.clean("A".repeat(500))).hasSize(OutputSanitizer.MAX_FIELD_LENGTH);
    }

    @Test
    void rejectsAbsurdOrMissingQuantities() {
        assertThat(OutputSanitizer.validQuantity(null)).isNull();
        assertThat(OutputSanitizer.validQuantity(0)).isNull();
        assertThat(OutputSanitizer.validQuantity(999)).isNull();
        assertThat(OutputSanitizer.validQuantity(3)).isEqualTo(3);
    }

    @Test
    void handlesNullExtraction() {
        assertThat(sanitizer.sanitize(null)).isEmpty();
        assertThat(sanitizer.sanitize(new VisionExtraction(false, null))).isEmpty();
    }
}
