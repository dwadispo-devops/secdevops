package ma.medidispo.pharmacy_service.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static ma.medidispo.pharmacy_service.notification.EmailDtos.*;

@SpringBootTest(properties = {"notifications.email.enabled=true", "notifications.email.delai-ms=3600000"})
@Testcontainers
class EmailOutboxIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));
    @Autowired EmailOutbox outbox;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JavaMailSender mail;
    @MockitoBean AuthContactClient contacts;
    UUID user, notification;

    @BeforeEach void donnees() {
        jdbc.update("DELETE FROM notifications");
        reset(mail, contacts);
        user = UUID.randomUUID(); notification = UUID.randomUUID();
        jdbc.update("INSERT INTO notifications (id, user_id, type, title, message, link) VALUES (?, ?, 'REPONSE_PHARMACIE', 'Médicaments disponibles', 'Pharmacie Atlas a vos médicaments (95.00 DH).', '/suivi-demande/x')",
            notification, user);
    }
    String statut() { return jdbc.queryForObject("SELECT email_status FROM notifications WHERE id = ?", String.class, notification); }

    @Test void envoieLEmailPuisLeMarqueEnvoye() {
        when(contacts.contact(user)).thenReturn(Optional.of(new Contact("amina@test.ma", "Amina")));
        assertThat(outbox.envoyer()).isEqualTo(1);
        ArgumentCaptor<SimpleMailMessage> envoye = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(envoye.capture());
        assertThat(envoye.getValue().getTo()).containsExactly("amina@test.ma");
        assertThat(statut()).isEqualTo("SENT");
        assertThat(outbox.envoyer()).isZero(); // jamais envoyé deux fois
    }

    @Test void ignoreUnUtilisateurInconnu() {
        when(contacts.contact(user)).thenReturn(Optional.empty());
        outbox.envoyer();
        verify(mail, never()).send(any(SimpleMailMessage.class));
        assertThat(statut()).isEqualTo("SKIPPED");
    }

    @Test void reessaieEnCasDePannePuisAbandonneApresCinqEchecs() {
        when(contacts.contact(user)).thenReturn(Optional.of(new Contact("amina@test.ma", "Amina")));
        doThrow(new MailSendException("serveur e-mail indisponible")).when(mail).send(any(SimpleMailMessage.class));
        outbox.envoyer();
        assertThat(statut()).isEqualTo("PENDING");
        for (int i = 0; i < 4; i++) outbox.envoyer();
        assertThat(statut()).isEqualTo("FAILED");
    }
}
