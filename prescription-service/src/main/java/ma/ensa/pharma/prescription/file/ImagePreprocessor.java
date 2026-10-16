package ma.ensa.pharma.prescription.file;

import ma.ensa.pharma.prescription.config.ReadingProperties;
import ma.ensa.pharma.prescription.domain.DetectedFileType;
import ma.ensa.pharma.prescription.exception.PrescriptionException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Transforme le fichier reçu en une image PNG "propre" envoyée au modèle vision :
 * <ul>
 *   <li>PDF : seule la première page est rendue en image (200 dpi) ;</li>
 *   <li>JPG / PNG : contrôle des dimensions avant décodage (anti bombe de décompression) ;</li>
 *   <li>redimensionnement si l'image est trop grande (temps de réponse, ENF02) ;</li>
 *   <li>ré-encodage en PNG RGB : supprime les métadonnées (EXIF, GPS...) avant l'envoi à l'IA.</li>
 * </ul>
 * Tout se fait en mémoire : aucune image n'est écrite sur disque (RG06).
 */
@Component
public class ImagePreprocessor {

    private static final int PDF_DPI = 200;

    private final ReadingProperties properties;

    public ImagePreprocessor(ReadingProperties properties) {
        this.properties = properties;
    }

    public PreparedImage prepare(byte[] content, DetectedFileType type) {
        try {
            BufferedImage source;
            boolean multiPage = false;
            if (type == DetectedFileType.PDF) {
                try (PDDocument document = Loader.loadPDF(content)) {
                    if (document.isEncrypted() || document.getNumberOfPages() == 0) {
                        throw PrescriptionException.unreadableImage(null);
                    }
                    multiPage = document.getNumberOfPages() > 1;
                    source = new PDFRenderer(document).renderImageWithDPI(0, PDF_DPI, ImageType.RGB);
                }
            } else {
                source = decodeSafely(content);
            }
            BufferedImage normalized = toRgb(resizeIfNeeded(source));
            return new PreparedImage(encodePng(normalized), normalized.getWidth(), normalized.getHeight(), multiPage);
        } catch (PrescriptionException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw PrescriptionException.unreadableImage(e);
        }
    }

    /** Lit les dimensions dans l'en-tête avant de décoder les pixels. */
    private BufferedImage decodeSafely(byte[] content) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw PrescriptionException.unreadableImage(null);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > properties.maxPixels()) {
                    throw PrescriptionException.imageTooBig();
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private BufferedImage resizeIfNeeded(BufferedImage image) {
        int maxSide = properties.maxImageSide();
        int longest = Math.max(image.getWidth(), image.getHeight());
        if (longest <= maxSide) {
            return image;
        }
        double ratio = (double) maxSide / longest;
        int width = Math.max(1, (int) Math.round(image.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(image.getHeight() * ratio));
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, width, height, java.awt.Color.WHITE, null);
        } finally {
            g.dispose();
        }
        return resized;
    }

    private static BufferedImage toRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            // Fond blanc pour les PNG transparents
            g.drawImage(image, 0, 0, java.awt.Color.WHITE, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private static byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) {
            throw new IOException("Aucun encodeur PNG disponible");
        }
        return out.toByteArray();
    }

    /**
     * Image prête à être envoyée au modèle.
     *
     * @param png       contenu PNG
     * @param width     largeur finale
     * @param height    hauteur finale
     * @param multiPage true si le PDF contenait plusieurs pages (seule la 1re est lue)
     */
    public record PreparedImage(byte[] png, int width, int height, boolean multiPage) {
    }
}
