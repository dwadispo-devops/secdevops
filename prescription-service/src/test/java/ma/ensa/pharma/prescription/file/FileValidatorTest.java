package ma.ensa.pharma.prescription.file;

import ma.ensa.pharma.prescription.TestImages;
import ma.ensa.pharma.prescription.domain.DetectedFileType;
import ma.ensa.pharma.prescription.exception.PrescriptionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileValidatorTest {

    private final FileValidator validator = new FileValidator();

    @Test
    void acceptsPng() {
        assertThat(validator.validate(TestImages.image("png", 50, 50), "ordo.png"))
                .isEqualTo(DetectedFileType.PNG);
    }

    @Test
    void acceptsJpegWithUppercaseExtension() {
        assertThat(validator.validate(TestImages.image("jpg", 50, 50), "ORDO.JPEG"))
                .isEqualTo(DetectedFileType.JPEG);
    }

    @Test
    void acceptsPdf() {
        assertThat(validator.validate(TestImages.pdf(1), "ordo.pdf")).isEqualTo(DetectedFileType.PDF);
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> validator.validate(new byte[0], "ordo.png"))
                .isInstanceOf(PrescriptionException.class)
                .extracting("code").isEqualTo("EMPTY_FILE");
    }

    @Test
    void rejectsFileOverFiveMegabytes() {
        byte[] big = new byte[(int) FileValidator.MAX_SIZE_BYTES + 1];
        big[0] = (byte) 0xFF;
        big[1] = (byte) 0xD8;
        big[2] = (byte) 0xFF;
        assertThatThrownBy(() -> validator.validate(big, "ordo.jpg"))
                .isInstanceOf(PrescriptionException.class)
                .extracting("code").isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    void rejectsForbiddenExtension() {
        assertThatThrownBy(() -> validator.validate("hello".getBytes(StandardCharsets.UTF_8), "script.exe"))
                .isInstanceOf(PrescriptionException.class)
                .extracting("code").isEqualTo("UNSUPPORTED_FILE_TYPE");
    }

    @Test
    void rejectsExecutableRenamedAsPng() {
        byte[] fakePng = "MZ\u0090\u0000 not an image".getBytes(StandardCharsets.ISO_8859_1);
        assertThatThrownBy(() -> validator.validate(fakePng, "ordo.png"))
                .isInstanceOf(PrescriptionException.class)
                .extracting("code").isEqualTo("UNSUPPORTED_FILE_TYPE");
    }

    @Test
    void rejectsPdfRenamedAsJpg() {
        assertThatThrownBy(() -> validator.validate(TestImages.pdf(1), "ordo.jpg"))
                .isInstanceOf(PrescriptionException.class)
                .extracting("code").isEqualTo("FILE_TYPE_MISMATCH");
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            "ordo.PNG, png",
            "photo.ordo.jpeg, jpeg",
            "sansextension, ''",
            "point., ''",
            "NULL, ''"
    })
    void extractsExtension(String filename, String expected) {
        assertThat(FileValidator.extensionOf(filename)).isEqualTo(expected);
    }
}
