package ma.medidispo.auth_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import ma.medidispo.auth_service.user.Role;
import ma.medidispo.auth_service.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static final String SECRET_A = Base64.getEncoder()
            .encodeToString("secret-A-secret-A-secret-A-secret-A-12345".getBytes());
    private static final String SECRET_B = Base64.getEncoder()
            .encodeToString("secret-B-secret-B-secret-B-secret-B-12345".getBytes());

    private User user() {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
        u.setEmail("titima@test.ma");
        u.setRole(Role.PATIENT);
        return u;
    }

    @Test
    void tokenValide_contientLesBonnesInfos() {
        JwtService service = new JwtService(SECRET_A, 15);
        User u = user();

        Claims claims = service.parse(service.generateAccessToken(u));

        assertEquals(u.getId().toString(), claims.getSubject());
        assertEquals("titima@test.ma", claims.get("email", String.class));
        assertEquals("PATIENT", claims.get("role", String.class));
    }

    @Test
    void tokenSigneAvecUnAutreSecret_estRefuse() {
        String token = new JwtService(SECRET_A, 15).generateAccessToken(user());

        assertThrows(JwtException.class, () -> new JwtService(SECRET_B, 15).parse(token));
    }

    @Test
    void tokenExpire_estRefuse() {
        JwtService service = new JwtService(SECRET_A, -1);
        String token = service.generateAccessToken(user());

        assertThrows(ExpiredJwtException.class, () -> service.parse(token));
    }

    @Test
    void secretTropCourt_estRefuse() {
        String court = Base64.getEncoder().encodeToString("trop-court".getBytes());

        assertThrows(IllegalStateException.class, () -> new JwtService(court, 15));
    }
}
