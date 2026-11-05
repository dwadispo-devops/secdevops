package ma.medidispo.pharmacy_service.stock;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.Base64;

class StockJwtServiceTest {

    private final StockJwtService service = new StockJwtService(
        Base64.getEncoder().encodeToString(new byte[32])
    );

    @Test
    void shouldRejectMissingBearerToken() {
        assertThrows(IllegalArgumentException.class,
            () -> service.authenticatedPharmacistId(null));
    }
    @Test
    void shouldRejectPatientRole() {
        String token = io.jsonwebtoken.Jwts.builder()
            .subject(java.util.UUID.randomUUID().toString())
            .claim("role", "PATIENT")
            .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(new byte[32]))
            .compact();

        assertThrows(SecurityException.class,
            () -> service.authenticatedPharmacistId("Bearer " + token));
    }
    @Test
    void shouldAcceptPharmacistRole() {
        java.util.UUID pharmacistId = java.util.UUID.randomUUID();

        String token = io.jsonwebtoken.Jwts.builder()
            .subject(pharmacistId.toString())
            .claim("role", "PHARMACIEN")
            .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(new byte[32]))
            .compact();

        assertEquals(pharmacistId,
            service.authenticatedPharmacistId("Bearer " + token));
    }
}