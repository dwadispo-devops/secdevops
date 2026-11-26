package ma.medidispo.pharmacy_service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static ma.medidispo.pharmacy_service.notification.EmailDtos.*;

/**
 * US14 / EF24 – Boîte d'envoi des e-mails (pattern « outbox »).
 * Les notifications sont créées dans la transaction métier ; cette tâche envoie ensuite les e-mails
 * par lots. Le métier ne dépend donc jamais du serveur e-mail, et un échec est réessayé (5 fois maximum).
 */
@Component
@ConditionalOnProperty(name = "notifications.email.enabled", havingValue = "true")
public class EmailOutbox {
    private static final Logger log = LoggerFactory.getLogger(EmailOutbox.class);
    static final int LOT = 20;
    static final int MAX_ESSAIS = 5;

    private final NotificationRepository repository;
    private final AuthContactClient contacts;
    private final JavaMailSender mail;
    private final String expediteur;
    private final String urlSite;

    public EmailOutbox(NotificationRepository repository, AuthContactClient contacts, JavaMailSender mail,
                       @Value("${notifications.email.from:no-reply@dwadispo.local}") String expediteur,
                       @Value("${app.front-url:http://localhost:4200}") String urlSite) {
        this.repository = repository; this.contacts = contacts; this.mail = mail;
        this.expediteur = expediteur; this.urlSite = urlSite;
    }

    @Scheduled(fixedDelayString = "${notifications.email.delai-ms:30000}", initialDelayString = "${notifications.email.delai-ms:30000}")
    @Transactional
    public int envoyer() {
        int envoyes = 0;
        for (EmailEnAttente n : repository.emailsEnAttente(LOT)) {
            UUID id = n.id();
            try {
                var contact = contacts.contact(n.userId());
                if (contact.isEmpty()) { repository.marquerEmail(id, "SKIPPED"); continue; }
                mail.send(EmailComposer.composer(expediteur, contact.get(), n, urlSite));
                repository.marquerEmail(id, "SENT");
                envoyes++;
            } catch (RuntimeException e) {
                repository.echecEmail(id, MAX_ESSAIS);
                // Aucune donnée personnelle dans les journaux (ENF05) : seulement l'identifiant technique.
                log.warn("Envoi de l'e-mail reporté pour la notification {} : {}", id, e.getClass().getSimpleName());
            }
        }
        return envoyes;
    }
}
