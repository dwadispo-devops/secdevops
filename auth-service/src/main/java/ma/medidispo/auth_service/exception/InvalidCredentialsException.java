package ma.medidispo.auth_service.exception;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("E-mail ou mot de passe incorrect");
    }
}
