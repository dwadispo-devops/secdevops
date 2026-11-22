package ma.ensa.pharma.prescription.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** US20 : consommateur du topic dwadispo.comptes (activé par comptes.kafka.enabled=true). */
@Component
@ConditionalOnProperty(name = "comptes.kafka.enabled", havingValue = "true")
public class KafkaAccountListener {

    public static final String TOPIC = "dwadispo.comptes";

    private static final Logger log = LoggerFactory.getLogger(KafkaAccountListener.class);
    private final ObjectMapper json = new ObjectMapper();
    private final PrescriptionAccountDeletion deletion;

    public KafkaAccountListener(PrescriptionAccountDeletion deletion) {
        this.deletion = deletion;
    }

    @KafkaListener(topics = TOPIC, groupId = "prescription-comptes")
    public void recevoir(String message) throws Exception {
        JsonNode e = json.readTree(message);
        if (!"COMPTE_SUPPRIME".equals(e.path("type").asText())) {
            return;
        }
        log.info("Événement COMPTE_SUPPRIME {} reçu depuis Kafka", e.path("id").asText());
        deletion.compteSupprime(UUID.fromString(e.path("userId").asText()), e.path("role").asText());
    }
}
