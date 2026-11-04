package ma.ensa.pharma.prescription.catalog;

/** Le catalogue (medication-service) ne répond pas ou renvoie une erreur. */
public class CatalogUnavailableException extends RuntimeException {

    public CatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
