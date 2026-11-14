package ma.ensa.pharma.prescription.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import ma.ensa.pharma.prescription.exception.PrescriptionException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ReviewDelegationServiceTest {
    private final byte[] key=new byte[32];
    private final ReviewDelegationService service=new ReviewDelegationService(Base64.getEncoder().encodeToString(key));
    private final UUID patient=UUID.randomUUID(), prescription=UUID.randomUUID();
    private String token(UUID resource,String audience,long expires,boolean issuer) {
        return "Bearer "+Jwts.builder().issuer(issuer?"pharmacy-service":"auth-service").subject(patient.toString())
            .claim("prescriptionId",resource.toString()).audience().add(audience).and()
            .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis()+expires)).signWith(Keys.hmacShaKeyFor(key)).compact();
    }
    @Test void acceptsOnlyScopedShortLivedDelegation() {
        assertThat(service.patient(token(prescription,"prescription-review",30000,true),prescription)).isEqualTo(patient);
    }
    @Test void rejectsPurgeAudience() {
        String purgeToken = token(prescription, "prescription-purge", 30000, true);
        assertThatThrownBy(() -> service.patient(purgeToken, prescription))
                .isInstanceOf(PrescriptionException.class)
                .satisfies(ex -> assertThat(((PrescriptionException) ex).getStatus().value()).isEqualTo(401));
    }
    @Test void rejectsMissingExpiredForeignAndOrdinaryUserTokens() {
        for (String auth:Arrays.asList(null,"Bearer invalid",token(prescription,"prescription-review",-1000,true),
                token(UUID.randomUUID(),"prescription-review",30000,true),token(prescription,"other",30000,true),
                token(prescription,"prescription-review",30000,false),token(prescription,"prescription-review",60000,true))) {
            assertThatThrownBy(() -> service.patient(auth,prescription)).isInstanceOf(PrescriptionException.class);
        }
    }
}
