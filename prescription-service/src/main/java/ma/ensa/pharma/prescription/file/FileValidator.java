package ma.ensa.pharma.prescription.file;

import ma.ensa.pharma.prescription.domain.DetectedFileType;
import ma.ensa.pharma.prescription.exception.PrescriptionException;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Validation du fichier envoyé (RG05) : non vide, taille maximale, extension autorisée
 * et type réel vérifié par la signature binaire ("magic bytes"), pas seulement par l'extension
 * ou le Content-Type envoyé par le client (qui peuvent être falsifiés).
 */
@Component
public class FileValidator {

    public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};

    public DetectedFileType validate(byte[] content, String originalFilename) {
        if (content == null || content.length == 0) {
            throw PrescriptionException.emptyFile();
        }
        if (content.length > MAX_SIZE_BYTES) {
            throw PrescriptionException.tooLarge(MAX_SIZE_BYTES);
        }

        String extension = extensionOf(originalFilename);
        if (!DetectedFileType.isAllowedExtension(extension)) {
            throw PrescriptionException.unsupportedType();
        }

        DetectedFileType detected = detect(content);
        if (detected == null) {
            throw PrescriptionException.unsupportedType();
        }
        if (!detected.acceptsExtension(extension)) {
            throw PrescriptionException.typeMismatch();
        }
        return detected;
    }

    static DetectedFileType detect(byte[] content) {
        if (startsWith(content, PNG_MAGIC)) {
            return DetectedFileType.PNG;
        }
        if (startsWith(content, JPEG_MAGIC)) {
            return DetectedFileType.JPEG;
        }
        if (startsWith(content, PDF_MAGIC)) {
            return DetectedFileType.PDF;
        }
        return null;
    }

    static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
