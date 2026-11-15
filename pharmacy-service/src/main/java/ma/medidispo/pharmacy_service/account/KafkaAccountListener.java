package ma.medidispo.pharmacy_service.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/** US20 : consommateur du topic dwadispo.comptes (activé par comptes.kafka.enabled=true). */
@Component
@ConditionalOnProperty(name = "comptes.kafka.enabled", havingValue = "true")
public class KafkaAccountListener {

    public static final String TOPIC = "dwadispo.comptes";

    private static final Logger log = LoggerFactory.getLogger(KafkaAccountListener.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final AccountDeletionHandler handler;

    public KafkaAccountListener(AccountDeletionHandler handler) {
        this.handler = handler;
    }

    @KafkaListener(topics = TOPIC, groupId = "pharmacy-comptes")
    public void recevoir(String message) {
        JsonNode e = json.readTree(message);
        if (!"COMPTE_SUPPRIME".equals(e.path("type").asString())) {
            return;
        }
        log.info("Événement COMPTE_SUPPRIME {} reçu depuis Kafka", e.path("id").asString());
        handler.compteSupprime(UUID.fromString(e.path("userId").asString()), e.path("role").asString());
    }
}
