package ma.medidispo.pharmacy_service.pharmacy;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PharmacyController.class)
public class PharmacyExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegrityViolation(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "23505".equals(violation.getSQLState())
                    && "pharmacies_license_number_key".equals(violation.getConstraintName())) {
                return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                        "Une pharmacie utilise deja ce numero de licence.");
            }
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Une erreur interne est survenue.");
    }
}
