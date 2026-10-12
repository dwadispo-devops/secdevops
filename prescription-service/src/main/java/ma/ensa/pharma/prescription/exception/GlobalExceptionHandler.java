package ma.ensa.pharma.prescription.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Réponses d'erreur au format RFC 7807 (ProblemDetail) avec un code stable pour le front. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PrescriptionException.class)
    public ProblemDetail handlePrescription(PrescriptionException e) {
        if (e.getStatus().is5xxServerError()) {
            log.warn("Erreur {} : {}", e.getCode(), e.getMessage());
        }
        return problem(e.getStatus(), e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleTooLarge(MaxUploadSizeExceededException e) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                "Le fichier dépasse la taille maximale autorisée (5 Mo).");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ProblemDetail handleMissingPart(MissingServletRequestPartException e) {
        return problem(HttpStatus.BAD_REQUEST, "FILE_REQUIRED", "Aucun fichier n'a été envoyé (champ 'file').");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String message) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(status.getReasonPhrase());
        detail.setProperty("code", code);
        return detail;
    }
}
