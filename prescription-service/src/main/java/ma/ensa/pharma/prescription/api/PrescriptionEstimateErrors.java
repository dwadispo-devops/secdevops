package ma.ensa.pharma.prescription.api;

import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

/** Erreurs sans reprise du contenu de l'ordonnance. Ne modifie pas les autres endpoints. */
@RestControllerAdvice(assignableTypes = PrescriptionEstimateController.class)
public class PrescriptionEstimateErrors {
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ProblemDetail invalid(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Fournissez de 1 à 100 lignes, un code de 13 chiffres et une quantité entière de 1 à 10000 par ligne.");
    }
}
