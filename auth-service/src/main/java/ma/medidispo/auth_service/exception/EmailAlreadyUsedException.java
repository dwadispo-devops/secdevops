package ma.medidispo.auth_service.exception;

public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException() {
        super("Un compte existe déjà avec cet e-mail");
    }
}
