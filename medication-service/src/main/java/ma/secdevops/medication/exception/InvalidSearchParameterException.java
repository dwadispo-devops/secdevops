package ma.secdevops.medication.exception;

/**
 * Exception levée lorsqu'un paramètre de recherche est invalide.
 */
public class InvalidSearchParameterException extends RuntimeException {
    public InvalidSearchParameterException(String message) {
        super(message);
    }
}
