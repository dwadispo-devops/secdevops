package ma.medidispo.pharmacy_service.review;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrescriptionClientTest {

    private final String base64Secret = Base64.getEncoder().encodeToString(new byte[32]);
    private final PrescriptionClient client = new PrescriptionClient("http://localhost:8085", base64Secret);

    @Test
    void purgeTokenContientLesClaimsRequisEtExpirationInferieureA30Secondes() {
        UUID patient = UUID.randomUUID();
        UUID prescription = UUID.randomUUID();

        String token = client.purgeToken(patient, prescription);
        assertThat(token).isNotEmpty();

        var key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

        assertThat(claims.getIssuer()).isEqualTo("pharmacy-service");
        assertThat(claims.getSubject()).isEqualTo(patient.toString());
        assertThat(claims.getAudience()).contains("prescription-purge");
        assertThat(claims.get("prescriptionId", String.class)).isEqualTo(prescription.toString());

        Date issuedAt = claims.getIssuedAt();
        Date expiration = claims.getExpiration();
        assertThat(issuedAt).isNotNull();
        assertThat(expiration).isNotNull();

        long diffMs = expiration.getTime() - issuedAt.getTime();
        assertThat(diffMs).isGreaterThan(0).isLessThanOrEqualTo(30000);
    }

    @Test
    void purgeImage_reponse204_aucuneExceptionEtRequeteConforme() throws Exception {
        UUID patient = UUID.randomUUID();
        UUID prescription = UUID.randomUUID();

        var receivedMethod = new AtomicReference<String>();
        var receivedPath = new AtomicReference<String>();
        var receivedAuth = new AtomicReference<String>();

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/prescriptions/" + prescription + "/image", exchange -> {
            receivedMethod.set(exchange.getRequestMethod());
            receivedPath.set(exchange.getRequestURI().getPath());
            receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            var testClient = new PrescriptionClient("http://localhost:" + port, base64Secret);

            testClient.purgeImage(patient, prescription);

            assertThat(receivedMethod.get()).isEqualTo("DELETE");
            assertThat(receivedPath.get()).isEqualTo("/internal/prescriptions/" + prescription + "/image");
            assertThat(receivedAuth.get()).startsWith("Bearer ");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void purgeImage_reponse404_leveException() throws Exception {
        UUID patient = UUID.randomUUID();
        UUID prescription = UUID.randomUUID();

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            var testClient = new PrescriptionClient("http://localhost:" + port, base64Secret);

            assertThatThrownBy(() -> testClient.purgeImage(patient, prescription))
                    .isInstanceOf(ResponseStatusException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void purgeImage_reponse500_leveException() throws Exception {
        UUID patient = UUID.randomUUID();
        UUID prescription = UUID.randomUUID();

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            var testClient = new PrescriptionClient("http://localhost:" + port, base64Secret);

            assertThatThrownBy(() -> testClient.purgeImage(patient, prescription))
                    .isInstanceOf(ResponseStatusException.class);
        } finally {
            server.stop(0);
        }
    }
}
