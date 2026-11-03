package ma.medidispo.pharmacy_service.stock;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.UUID;

@Service
public class StockJwtService {

    private final SecretKey key;

    public StockJwtService(@Value("${app.jwt.secret}") String secret) {
        byte[] bytes = Decoders.BASE64.decode(secret);

        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET invalide");
        }

        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public UUID authenticatedPharmacistId(String authorization) {

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Token Bearer manquant");
        }

        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(authorization.substring(7))
                .getPayload();

        if (!"PHARMACIEN".equals(claims.get("role", String.class))) {
            throw new SecurityException("Acces reserve aux pharmaciens");
        }

        String subject = claims.getSubject();

        if (subject == null || subject.isBlank()) {
            throw new SecurityException("Identifiant pharmacien manquant");
        }

        return UUID.fromString(subject);
    }
}

