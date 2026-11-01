
package ma.medidispo.pharmacy_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Set;
import java.util.UUID;

@Service
public class JwtService {

    private static final Set<String> ALLOWED_ROLES =
            Set.of("PATIENT", "PHARMACIEN", "ADMIN");

    private final SecretKey key;

    public JwtService(@Value("${app.jwt.secret}") String secret) {
        byte[] bytes = Decoders.BASE64.decode(secret);

        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET doit contenir au minimum 32 octets."
            );
        }

        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID getUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public String getRole(Claims claims) {
        String role = claims.get("role", String.class);

        if (role == null || !ALLOWED_ROLES.contains(role)) {
            throw new IllegalArgumentException("Rôle JWT invalide.");
        }

        return role;
    }
}
