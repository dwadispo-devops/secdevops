package ma.medidispo.pharmacy_service.review;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import static org.springframework.http.HttpStatus.*;

/** Short-lived, resource-scoped delegation. Never forwards a pharmacist token as a patient token. */
@Component
public class PrescriptionClient {
    private final RestClient client;
    private final SecretKey key;
    public PrescriptionClient(@Value("${prescription.service-url:http://localhost:8085}") String url,
                              @Value("${app.jwt.secret}") String secret) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
    private String token(UUID patient, UUID prescription) {
        var now = Instant.now();
        return Jwts.builder().issuer("pharmacy-service").subject(patient.toString())
            .audience().add("prescription-review").and().claim("prescriptionId", prescription.toString())
            .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(30))).signWith(key).compact();
    }
    public ReviewDtos.Prescription details(UUID patient, UUID prescription) {
        return call(() -> client.get().uri("/internal/prescriptions/{id}", prescription)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(patient, prescription))
            .retrieve().body(ReviewDtos.Prescription.class));
    }
    public ResponseEntity<byte[]> image(UUID patient, UUID prescription) {
        return call(() -> client.get().uri("/internal/prescriptions/{id}/image", prescription)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(patient, prescription))
            .retrieve().toEntity(byte[].class));
    }
    private <T> T call(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) throw new ResponseStatusException(NOT_FOUND, "Ordonnance ou image indisponible");
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Service ordonnance indisponible");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Service ordonnance indisponible");
        }
    }
}
