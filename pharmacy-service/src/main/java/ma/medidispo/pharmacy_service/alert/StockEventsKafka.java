package ma.medidispo.pharmacy_service.alert;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

import static ma.medidispo.pharmacy_service.alert.StockAlertDtos.StockAvailableEvent;

/**
 * US22 : retours en stock publiés dans Kafka (topic dwadispo.stock), activé par stock.kafka.enabled=true.
 * Le pharmacien n'attend pas : il publie un événement, les patients sont prévenus en arrière-plan.
 * Si Kafka est indisponible, l'événement est traité directement (aucune alerte perdue).
 */
@Configuration
@ConditionalOnProperty(name = "stock.kafka.enabled", havingValue = "true")
public class StockEventsKafka {

    public static final String TOPIC = "dwadispo.stock";

    private static final Logger log = LoggerFactory.getLogger(StockEventsKafka.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final KafkaTemplate<String, String> kafka;
    private final StockAlertTrigger trigger;

    public StockEventsKafka(KafkaTemplate<String, String> kafka, StockAlertTrigger trigger) {
        this.kafka = kafka;
        this.trigger = trigger;
    }

    @Bean
    public NewTopic stockTopic() {
        return TopicBuilder.name(TOPIC).partitions(3).replicas(1).build();
    }

    /** Publié seulement si la mise à jour du stock a été enregistrée (AFTER_COMMIT). Clé = médicament. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publier(StockAvailableEvent event) {
        try {
            kafka.send(TOPIC, event.medicationCode(), json.writeValueAsString(event))
                .whenComplete((r, erreur) -> {
                    if (erreur == null) {
                        log.info("Événement STOCK_DISPONIBLE {} publié dans Kafka (partition {})",
                            event.id(), r.getRecordMetadata().partition());
                    } else {
                        secours(event, erreur);
                    }
                });
        } catch (RuntimeException erreur) {
            secours(event, erreur);
        }
    }

    @KafkaListener(topics = TOPIC, groupId = "alertes-stock")
    public void recevoir(String message) {
        StockAvailableEvent event = json.readValue(message, StockAvailableEvent.class);
        log.info("Événement STOCK_DISPONIBLE {} reçu depuis Kafka", event.id());
        trigger.traiter(event);
    }

    private void secours(StockAvailableEvent event, Throwable erreur) {
        log.warn("Kafka indisponible ({}), traitement direct de l'événement {}", erreur.getMessage(), event.id());
        trigger.traiter(event);
    }
}
