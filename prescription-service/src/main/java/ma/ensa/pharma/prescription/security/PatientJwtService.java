package ma.ensa.pharma.prescription.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import ma.ensa.pharma.prescription.exception.PrescriptionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.UUID;

/**
 * US06 – Vérifie le JWT émis par auth-service et renvoie l'identifiant du patient.
 * Même principe que StockJwtService (pharmacy-service), avec le rôle PATIENT.
 */
@Service
public class PatientJwtService {

    private static final String BEARER = "Bearer ";
    private static final String PATIENT_ROLE = "PATIENT";

    private final SecretKey key;

    /** La clé JWT_SECRET (Base64) est la même que celle d'auth-service. */
    public PatientJwtService(@Value("${app.jwt.secret}") String secret) {
        byte[] bytes = Decoders.BASE64.decode(secret);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET invalide");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    /**
     * @param authorization valeur de l'en-tête "Authorization" (ex. "Bearer eyJ...")
     * @return l'UUID du patient connecté
     * @throws PrescriptionException 401 si le jeton est absent ou invalide, 403 si ce n'est pas un patient
     */
    public UUID authenticatedPatientId(String authorization) {
        // 1. Le jeton est-il présent ?
        if (authorization == null || !authorization.startsWith(BEARER)) {
            throw PrescriptionException.unauthenticated();
        }

        // 2. La signature est-elle bonne et le jeton non expiré ?
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(authorization.substring(BEARER.length()))
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw PrescriptionException.unauthenticated();
        }

        // 3. Est-ce bien un patient ?
        if (!PATIENT_ROLE.equals(claims.get("role", String.class))) {
            throw PrescriptionException.patientOnly();
        }

        // 4. Récupérer son identifiant
        try {
            return UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw PrescriptionException.unauthenticated();
        }
    }
}
