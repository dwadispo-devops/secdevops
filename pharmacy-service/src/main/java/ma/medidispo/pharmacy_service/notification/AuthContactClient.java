package ma.medidispo.pharmacy_service.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import java.util.Optional;
import java.util.UUID;
import static ma.medidispo.pharmacy_service.notification.EmailDtos.*;

/** US14 – Récupère l'adresse e-mail d'un utilisateur auprès d'auth-service (appel interne protégé par jeton). */
@Component
public class AuthContactClient {
    private final RestClient http;
    private final String jeton;

    public AuthContactClient(@Value("${auth.service-url:http://localhost:8081}") String url,
                             @Value("${app.internal-token:}") String jeton) {
        SimpleClientHttpRequestFactory delais = new SimpleClientHttpRequestFactory();
        delais.setConnectTimeout(3000);
        delais.setReadTimeout(3000);
        this.http = RestClient.builder().baseUrl(url).requestFactory(delais).build();
        this.jeton = jeton;
    }

    /** Vide si l'utilisateur n'existe plus ou est désactivé. */
    public Optional<Contact> contact(UUID user) {
        try {
            return Optional.ofNullable(http.get().uri("/internal/users/{id}/contact", user)
                .header("X-Internal-Token", jeton).retrieve().body(Contact.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
