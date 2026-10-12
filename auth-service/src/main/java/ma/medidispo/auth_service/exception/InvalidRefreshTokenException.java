package ma.medidispo.auth_service.exception;

public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh token invalide ou expiré");
    }
}
