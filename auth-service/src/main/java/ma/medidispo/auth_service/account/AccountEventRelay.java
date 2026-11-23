package ma.medidispo.auth_service.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * US20 : publie dans Kafka les événements de compte enregistrés dans l'outbox.
 * Si Kafka est indisponible, l'événement reste en base et sera republié au prochain passage :
 * l'ordre d'effacer les données dans les autres services n'est jamais perdu.
 * Clé du message = identifiant de l'utilisateur. Aucune donnée personnelle dans les logs.
 */
@Component
@ConditionalOnProperty(name = "comptes.kafka.enabled", havingValue = "true")
public class AccountEventRelay {

    private static final Logger log = LoggerFactory.getLogger(AccountEventRelay.class);

    private final AccountEventRepository events;
    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json = JsonMapper.builder().build();

    public AccountEventRelay(AccountEventRepository events, KafkaTemplate<String, String> kafka) {
        this.events = events;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${comptes.kafka.delai-ms:5000}")
    @Transactional
    public void publier() {
        for (AccountEvent e : events.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            try {
                kafka.send(AccountEventsKafkaConfig.TOPIC, e.getUserId().toString(), message(e))
                     .get(5, TimeUnit.SECONDS);
                e.markPublished();
                log.info("Événement {} {} publié dans Kafka", e.getType(), e.getId());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                log.warn("Kafka indisponible, l'événement {} sera republié plus tard : {}", e.getId(), ex.getMessage());
                return;
            }
        }
    }

    private String message(AccountEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId().toString());
        m.put("type", e.getType());
        m.put("userId", e.getUserId().toString());
        m.put("role", e.getRole());
        m.put("occurredAt", e.getCreatedAt().toString());
        return json.writeValueAsString(m);
    }
}
