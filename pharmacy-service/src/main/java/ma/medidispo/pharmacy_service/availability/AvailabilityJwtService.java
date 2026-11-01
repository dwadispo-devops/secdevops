package ma.medidispo.pharmacy_service.availability;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.UUID;

@Service
public class AvailabilityJwtService {

    private final SecretKey key;

    public AvailabilityJwtService(@Value("${app.jwt.secret}") String secret) {
        byte[] bytes = Decoders.BASE64.decode(secret);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET doit contenir au moins 32 octets");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public UUID authenticatedPatientId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new SecurityException("Token Bearer manquant");
        }

        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(authorization.substring(7))
                .getPayload();

        if (!"PATIENT".equals(claims.get("role", String.class))) {
            throw new SecurityException("Acces reserve aux patients");
        }

        return UUID.fromString(claims.getSubject());
    }
}