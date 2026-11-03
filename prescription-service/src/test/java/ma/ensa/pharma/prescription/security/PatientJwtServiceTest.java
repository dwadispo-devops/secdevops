package ma.ensa.pharma.prescription.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import ma.ensa.pharma.prescription.exception.PrescriptionException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatientJwtServiceTest {

    static final String SECRET = Base64.getEncoder().encodeToString(
            "cle-de-test-de-32-octets-minimum!".getBytes());

    private final PatientJwtService service = new PatientJwtService(SECRET);

    /** Fabrique un jeton signé comme le fait auth-service. */
    static String token(String secret, String subject, String role, long validityMs) {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        return "Bearer " + Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + validityMs))
                .signWith(key)
                .compact();
    }

    @Test
    void renvoieLIdDuPatient() {
        UUID id = UUID.randomUUID();
        assertThat(service.authenticatedPatientId(token(SECRET, id.toString(), "PATIENT", 60_000))).isEqualTo(id);
    }

    @Test
    void refuseSansJeton() {
        assertStatus(() -> service.authenticatedPatientId(null), HttpStatus.UNAUTHORIZED);
        assertStatus(() -> service.authenticatedPatientId("Basic abc"), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refuseUnJetonExpire() {
        String expired = token(SECRET, UUID.randomUUID().toString(), "PATIENT", -60_000);
        assertStatus(() -> service.authenticatedPatientId(expired), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refuseUnJetonSigneAvecUneAutreCle() {
        String other = Base64.getEncoder().encodeToString("une-autre-cle-de-32-octets-mini!!".getBytes());
        String forged = token(other, UUID.randomUUID().toString(), "PATIENT", 60_000);
        assertStatus(() -> service.authenticatedPatientId(forged), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refuseUnPharmacien() {
        String pharmacist = token(SECRET, UUID.randomUUID().toString(), "PHARMACIEN", 60_000);
        assertStatus(() -> service.authenticatedPatientId(pharmacist), HttpStatus.FORBIDDEN);
    }

    @Test
    void refuseUnSujetQuiNEstPasUnUuid() {
        String bad = token(SECRET, "pas-un-uuid", "PATIENT", 60_000);
        assertStatus(() -> service.authenticatedPatientId(bad), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refuseUnSecretTropCourt() {
        String shortSecret = Base64.getEncoder().encodeToString("court".getBytes());
        assertThatThrownBy(() -> new PatientJwtService(shortSecret)).isInstanceOf(IllegalStateException.class);
    }

    private static void assertStatus(Runnable call, HttpStatus expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(PrescriptionException.class)
                .extracting(e -> ((PrescriptionException) e).getStatus())
                .isEqualTo(expected);
    }
}
