package ma.ensa.pharma.prescription.domain;

import java.util.Set;

/** Types de fichiers acceptés pour le scan (RG05), reconnus par leur signature binaire. */
public enum DetectedFileType {
    JPEG(Set.of("jpg", "jpeg")),
    PNG(Set.of("png")),
    PDF(Set.of("pdf"));

    private final Set<String> extensions;

    DetectedFileType(Set<String> extensions) {
        this.extensions = extensions;
    }

    public boolean acceptsExtension(String extension) {
        return extensions.contains(extension);
    }

    public static boolean isAllowedExtension(String extension) {
        for (DetectedFileType type : values()) {
            if (type.acceptsExtension(extension)) {
                return true;
            }
        }
        return false;
    }
}
