package ma.medidispo.pharmacy_service.notification;

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

/**
 * US14 – Notifications asynchrones avec Kafka (activé par notifications.kafka.enabled=true).
 *  - Producteur : après la transaction métier, l'événement est publié dans le topic (clé = demande ou réservation,
 *    pour garder l'ordre des événements d'une même demande dans une même partition).
 *  - Consommateur : 3 lecteurs en parallèle (1 par partition) créent les notifications.
 *  - Si Kafka est indisponible, l'événement est traité directement : aucune notification n'est perdue.
 */
@Configuration
@ConditionalOnProperty(name = "notifications.kafka.enabled", havingValue = "true")
public class KafkaNotifications {

    public static final String TOPIC = "dwadispo.notifications";
    static final int PARTITIONS = 3;

    private static final Logger log = LoggerFactory.getLogger(KafkaNotifications.class);
    private final JsonMapper json = JsonMapper.builder().build();

    private final KafkaTemplate<String, String> kafka;
    private final NotificationHandler handler;

    public KafkaNotifications(KafkaTemplate<String, String> kafka, NotificationHandler handler) {
        this.kafka = kafka;
        this.handler = handler;
    }

    /** Crée le topic au démarrage s'il n'existe pas. */
    @Bean
    public NewTopic notificationsTopic() {
        return TopicBuilder.name(TOPIC).partitions(PARTITIONS).replicas(1).build();
    }

    /** Producteur : publié seulement si la transaction métier a réussi (AFTER_COMMIT). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publier(NotificationEvent evenement) {
        try {
            kafka.send(TOPIC, evenement.reference().toString(), json.writeValueAsString(evenement))
                .whenComplete((resultat, erreur) -> {
                    if (erreur == null) {
                        log.info("Événement {} {} publié dans Kafka (partition {})", evenement.type(), evenement.id(),
                            resultat.getRecordMetadata().partition());
                    } else {
                        secours(evenement, erreur);
                    }
                });
        } catch (RuntimeException erreur) {
            secours(evenement, erreur);
        }
    }

    /** Consommateur : 3 lecteurs en parallèle, groupe "notifications". */
    @KafkaListener(topics = TOPIC, groupId = "notifications", concurrency = "3")
    public void recevoir(String message) {
        NotificationEvent evenement = json.readValue(message, NotificationEvent.class);
        log.info("Événement {} {} reçu depuis Kafka", evenement.type(), evenement.id());
        handler.traiter(evenement);
    }

    private void secours(NotificationEvent evenement, Throwable erreur) {
        log.warn("Kafka indisponible ({}), traitement direct de l'événement {}", erreur.getMessage(), evenement.id());
        handler.traiter(evenement);
    }
}
