package ma.medidispo.auth_service.auth;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void requeteValide() {
        var request = new RegisterRequest("a@b.ma", "Secret123", "Titima", "Test");
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void emailInvalide() {
        var request = new RegisterRequest("pas-un-email", "Secret123", "Titima", "Test");
        assertEquals(1, validator.validate(request).size());
    }

    @Test
    void motDePasseTropCourt() {
        var request = new RegisterRequest("a@b.ma", "ab1", "Titima", "Test");
        assertEquals(1, validator.validate(request).size());
    }

    @Test
    void motDePasseSansChiffre() {
        var request = new RegisterRequest("a@b.ma", "SecretSecret", "Titima", "Test");
        assertEquals(1, validator.validate(request).size());
    }

    @Test
    void prenomVide() {
        var request = new RegisterRequest("a@b.ma", "Secret123", " ", "Test");
        assertEquals(1, validator.validate(request).size());
    }
}
