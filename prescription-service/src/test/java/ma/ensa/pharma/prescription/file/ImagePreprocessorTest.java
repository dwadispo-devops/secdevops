package ma.ensa.pharma.prescription.file;

import ma.ensa.pharma.prescription.TestImages;
import ma.ensa.pharma.prescription.config.ReadingProperties;
import ma.ensa.pharma.prescription.domain.DetectedFileType;
import ma.ensa.pharma.prescription.exception.PrescriptionException;
import ma.ensa.pharma.prescription.file.ImagePreprocessor.PreparedImage;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImagePreprocessorTest {

    private final ImagePreprocessor preprocessor =
            new ImagePreprocessor(new ReadingProperties(1000, 4_000_000, 25));

    @Test
    void keepsSmallImageSizeAndOutputsRgbPng() throws IOException {
        PreparedImage prepared = preprocessor.prepare(TestImages.image("png", 400, 300), DetectedFileType.PNG);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(prepared.png()));
        assertThat(decoded.getWidth()).isEqualTo(400);
        assertThat(decoded.getHeight()).isEqualTo(300);
        assertThat(decoded.getColorModel().hasAlpha()).isFalse();
        assertThat(prepared.multiPage()).isFalse();
    }

    @Test
    void resizesLargeJpegKeepingRatio() {
        PreparedImage prepared = preprocessor.prepare(TestImages.image("jpg", 1800, 1200), DetectedFileType.JPEG);

        assertThat(prepared.width()).isEqualTo(1000);
        assertThat(prepared.height()).isEqualTo(667);
    }

    @Test
    void rendersFirstPdfPageAndFlagsMultiPage() {
        PreparedImage prepared = preprocessor.prepare(TestImages.pdf(2), DetectedFileType.PDF);

        assertThat(prepared.png()).isNotEmpty();
        assertThat(prepared.multiPage()).isTrue();
        assertThat(Math.max(prepared.width(), prepared.height())).isLessThanOrEqualTo(1000);
    }

    @Test
    void rejectsImageWithTooManyPixels() {
        // 2500 x 2000 = 5 Mpx > limite de 4 Mpx configurée dans ce test
        assertThatThrownBy(() -> preprocessor.prepare(TestImages.image("png", 2500, 2000), DetectedFileType.PNG))
                .isInstanceOf(PrescriptionException.class)
                .extracting("code").isEqualTo("IMAGE_DIMENSIONS_TOO_LARGE");
    }

    @Test
    void rejectsCorruptedImage() {
        byte[] corrupted = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};
        assertThatThrownBy(() -> preprocessor.prepare(corrupted, DetectedFileType.PNG))
                .isInstanceOf(PrescriptionException.class)
                .extracting("code").isEqualTo("UNREADABLE_FILE");
    }

    @Test
    void rejectsCorruptedPdf() {
        byte[] corrupted = "%PDF-1.7 tronqué".getBytes();
        assertThatThrownBy(() -> preprocessor.prepare(corrupted, DetectedFileType.PDF))
                .isInstanceOf(PrescriptionException.class)
                .extracting("code").isEqualTo("UNREADABLE_FILE");
    }
}
