package ma.medidispo.pharmacy_service.notification;

import org.springframework.mail.SimpleMailMessage;
import static ma.medidispo.pharmacy_service.notification.EmailDtos.*;

/** US14 – Contenu des e-mails : même texte que la notification, jamais le détail des médicaments (ENF05). */
public final class EmailComposer {
    private EmailComposer() {}

    public static SimpleMailMessage composer(String expediteur, Contact contact, EmailEnAttente n, String urlSite) {
        StringBuilder texte = new StringBuilder();
        texte.append("Bonjour ").append(contact.firstName() == null ? "" : contact.firstName()).append(",\n\n");
        texte.append(n.message()).append("\n\n");
        if (n.link() != null && n.link().startsWith("/")) {
            texte.append("Voir le détail : ").append(urlSite).append(n.link()).append("\n\n");
        }
        texte.append("—\nDwaDispo · Merci de ne pas répondre à cet e-mail.\n")
             .append("Pour protéger vos données de santé, nos e-mails ne contiennent jamais le détail de vos médicaments.");
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(expediteur);
        mail.setTo(contact.email());
        mail.setSubject("DwaDispo – " + n.title());
        mail.setText(texte.toString());
        return mail;
    }
}
