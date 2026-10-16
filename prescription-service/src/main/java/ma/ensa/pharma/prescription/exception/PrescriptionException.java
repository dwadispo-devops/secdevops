package ma.ensa.pharma.prescription.exception;

import org.springframework.http.HttpStatus;

/**
 * Erreur fonctionnelle renvoyée au client avec un code stable (exploitable par le front Angular)
 * et un message en français. Le message ne doit jamais contenir de donnée de l'ordonnance.
 */
public class PrescriptionException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public PrescriptionException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public PrescriptionException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    // --- Fabriques pour les cas courants -------------------------------------------------------

    public static PrescriptionException consentRequired() {
        return new PrescriptionException(HttpStatus.BAD_REQUEST, "CONSENT_REQUIRED",
                "Le consentement du patient est requis avant l'envoi de l'image à l'IA.");
    }

    public static PrescriptionException emptyFile() {
        return new PrescriptionException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Le fichier est vide.");
    }

    public static PrescriptionException tooLarge(long maxBytes) {
        return new PrescriptionException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                "Le fichier dépasse la taille maximale autorisée (" + (maxBytes / (1024 * 1024)) + " Mo).");
    }

    public static PrescriptionException unsupportedType() {
        return new PrescriptionException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE",
                "Type de fichier non accepté. Formats autorisés : JPG, PNG, PDF.");
    }

    public static PrescriptionException typeMismatch() {
        return new PrescriptionException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FILE_TYPE_MISMATCH",
                "Le contenu du fichier ne correspond pas à son extension.");
    }

    public static PrescriptionException unreadableImage(Throwable cause) {
        return new PrescriptionException(HttpStatus.UNPROCESSABLE_ENTITY, "UNREADABLE_FILE",
                "Le fichier n'a pas pu être lu. Reprenez la photo ou importez un autre fichier.", cause);
    }

    public static PrescriptionException imageTooBig() {
        return new PrescriptionException(HttpStatus.UNPROCESSABLE_ENTITY, "IMAGE_DIMENSIONS_TOO_LARGE",
                "Les dimensions de l'image sont trop grandes.");
    }

    public static PrescriptionException visionUnavailable(Throwable cause) {
        return new PrescriptionException(HttpStatus.SERVICE_UNAVAILABLE, "VISION_UNAVAILABLE",
                "La lecture automatique est indisponible pour le moment. Vous pouvez saisir vos médicaments à la main.",
                cause);
    }
}
