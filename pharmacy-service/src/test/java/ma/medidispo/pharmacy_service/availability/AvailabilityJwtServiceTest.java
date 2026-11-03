package ma.medidispo.pharmacy_service.availability;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AvailabilityJwtServiceTest {

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"Basic abc", "bearer abc"})
    void missingOrUnsupportedAuthorizationIsRejected(String authorization) {
        assertThrows(SecurityException.class, () -> service.authenticatedPatientId(authorization));
    }

    private static final String SECRET =
            "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=";

    private final AvailabilityJwtService service =
            new AvailabilityJwtService(SECRET);

    private String token(String secret, String role, Date expiration) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));

        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", role)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    @Test
    void validPatientJwtIsAccepted() {
        String jwt = token(SECRET, "PATIENT",
                new Date(System.currentTimeMillis() + 60000));

        assertNotNull(service.authenticatedPatientId("Bearer " + jwt));
    }

    @Test
    void invalidSignatureIsRejected() {
        String otherSecret =
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

        String jwt = token(otherSecret, "PATIENT",
                new Date(System.currentTimeMillis() + 60000));

        assertThrows(io.jsonwebtoken.JwtException.class,
                () -> service.authenticatedPatientId("Bearer " + jwt));
    }

    @Test
    void expiredJwtIsRejected() {
        String jwt = token(SECRET, "PATIENT",
                new Date(System.currentTimeMillis() - 60000));

        assertThrows(io.jsonwebtoken.JwtException.class,
                () -> service.authenticatedPatientId("Bearer " + jwt));
    }

    @Test
    void pharmacyRoleIsRejected() {
        String jwt = token(SECRET, "PHARMACY",
                new Date(System.currentTimeMillis() + 60000));

        assertThrows(SecurityException.class,
                () -> service.authenticatedPatientId("Bearer " + jwt));
    }
}
