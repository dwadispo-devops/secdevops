package ma.ensa.pharma.prescription.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import ma.ensa.pharma.prescription.exception.PrescriptionException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PurgeDelegationServiceTest {
    private final byte[] key = new byte[32];
    private final PurgeDelegationService service = new PurgeDelegationService(Base64.getEncoder().encodeToString(key));
    private final UUID patient = UUID.randomUUID(), prescription = UUID.randomUUID();

    private String token(UUID resource, String audience, long expires, boolean validIssuer, byte[] signingKey) {
        return "Bearer " + Jwts.builder().issuer(validIssuer ? "pharmacy-service" : "auth-service").subject(patient.toString())
            .claim("prescriptionId", resource.toString()).audience().add(audience).and()
            .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + expires))
            .signWith(Keys.hmacShaKeyFor(signingKey)).compact();
    }

    private String token(UUID resource, String audience, long expires, boolean validIssuer) {
        return token(resource, audience, expires, validIssuer, key);
    }

    @Test
    void acceptsValidPurgeDelegation() {
        assertThat(service.patient(token(prescription, "prescription-purge", 30000, true), prescription))
            .isEqualTo(patient);
    }

    @Test
    void rejectsReviewAudience() {
        // Un jeton prescription-review ne doit pas être accepté pour la purge
        assertThatThrownBy(() -> service.patient(token(prescription, "prescription-review", 30000, true), prescription))
            .isInstanceOf(PrescriptionException.class);
    }

    @Test
    void rejectsMissingExpiredForeignAndInvalidTokens() {
        byte[] wrongKey = new byte[32];
        wrongKey[0] = 1;

        for (String auth : Arrays.asList(
                null,
                "Bearer invalid",
                token(prescription, "prescription-purge", -1000, true), // expiré
                token(UUID.randomUUID(), "prescription-purge", 30000, true), // mauvais prescriptionId
                token(prescription, "other-aud", 30000, true), // mauvaise audience
                token(prescription, "prescription-purge", 30000, false), // mauvais issuer
                token(prescription, "prescription-purge", 60000, true), // durée > 30s
                token(prescription, "prescription-purge", 30000, true, wrongKey) // mauvaise signature
        )) {
            assertThatThrownBy(() -> service.patient(auth, prescription)).isInstanceOf(PrescriptionException.class);
        }
    }
}

