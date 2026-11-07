package ma.medidispo.pharmacy_service.notification;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static ma.medidispo.pharmacy_service.notification.EmailDtos.*;

class EmailComposerTest {
    @Test void reprendLaNotificationAvecUnLienVersLeSite() {
        var n = new EmailEnAttente(UUID.randomUUID(), UUID.randomUUID(), "Médicaments disponibles",
            "Pharmacie Atlas a vos médicaments (95.00 DH).", "/suivi-demande/abc");
        var mail = EmailComposer.composer("no-reply@dwadispo.local", new Contact("amina@test.ma", "Amina"), n, "http://localhost:4200");
        assertThat(mail.getTo()).containsExactly("amina@test.ma");
        assertThat(mail.getSubject()).isEqualTo("DwaDispo – Médicaments disponibles");
        assertThat(mail.getText()).contains("Bonjour Amina", "Pharmacie Atlas a vos médicaments (95.00 DH).",
            "http://localhost:4200/suivi-demande/abc", "ne contiennent jamais le détail de vos médicaments");
    }

    @Test void ignoreUnLienQuiNEstPasInterne() {
        var n = new EmailEnAttente(UUID.randomUUID(), UUID.randomUUID(), "T", "M", "https://site-pirate.example");
        var mail = EmailComposer.composer("a@b", new Contact("c@d", "C"), n, "http://localhost:4200");
        assertThat(mail.getText()).doesNotContain("site-pirate");
    }
}
