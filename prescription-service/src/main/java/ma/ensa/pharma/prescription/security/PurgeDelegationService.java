package ma.ensa.pharma.prescription.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import ma.ensa.pharma.prescription.exception.PrescriptionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.UUID;

/** Accepts only short-lived resource-scoped server delegation for image purge. */
@Service
public class PurgeDelegationService {
    private final SecretKey key;

    public PurgeDelegationService(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    public UUID patient(String authorization, UUID prescription) {
        try {
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                throw new IllegalArgumentException();
            }
            var claims = Jwts.parser().verifyWith(key)
                .requireIssuer("pharmacy-service")
                .requireAudience("prescription-purge")
                .require("prescriptionId", prescription.toString())
                .build().parseSignedClaims(authorization.substring(7)).getPayload();

            if (claims.getExpiration() == null || claims.getIssuedAt() == null
                || claims.getExpiration().getTime() - claims.getIssuedAt().getTime() > 30000) {
                throw new IllegalArgumentException();
            }
            return UUID.fromString(claims.getSubject());
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException | NullPointerException ex) {
            throw PrescriptionException.unauthenticated();
        }
    }
}

