package ma.medidispo.auth_service.account;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

/** US20 : topic des événements de compte et relais planifié (activé par comptes.kafka.enabled=true). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "comptes.kafka.enabled", havingValue = "true")
public class AccountEventsKafkaConfig {

    public static final String TOPIC = "dwadispo.comptes";

    @Bean
    public NewTopic comptesTopic() {
        return TopicBuilder.name(TOPIC).partitions(3).replicas(1).build();
    }
}
