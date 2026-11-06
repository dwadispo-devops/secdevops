package ma.ensa.pharma.prescription.ai;

import ma.ensa.pharma.prescription.domain.VisionExtraction;
import ma.ensa.pharma.prescription.exception.PrescriptionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

/**
 * Implémentation Spring AI : envoie l'image au modèle de chat multimodal configuré
 * (Gemini par défaut, Ollama avec le profil "ollama") et convertit la réponse en
 * {@link VisionExtraction} grâce à la sortie structurée ({@code .entity(...)}).
 */
@Component
public class SpringAiVisionReader implements VisionReader {

    private static final Logger log = LoggerFactory.getLogger(SpringAiVisionReader.class);

    private final ChatClient chatClient;
    private final String provider;

    public SpringAiVisionReader(ChatClient.Builder chatClientBuilder,
                                @Value("${spring.ai.model.chat:google-genai}") String provider) {
        this.chatClient = chatClientBuilder
                .defaultSystem(PrescriptionPrompts.SYSTEM)
                .build();
        this.provider = provider;
    }

    /** Une réponse JSON incomplète (coupée par le modèle) est redemandée une fois. */
    static final int MAX_ATTEMPTS = 2;

    @Override
    public VisionExtraction read(byte[] png) {
        for (int attempt = 1; ; attempt++) {
            try {
                VisionExtraction extraction = chatClient.prompt()
                        .user(u -> u.text(PrescriptionPrompts.USER)
                                .media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(png)))
                        .call()
                        .entity(VisionExtraction.class);
                if (extraction == null) {
                    throw new IllegalStateException("Réponse vide du modèle");
                }
                return extraction;
            } catch (RuntimeException e) {
                Throwable root = rootCause(e);
                if (isInvalidJson(root) && attempt < MAX_ATTEMPTS) {
                    // Réponse coupée ou mal formée : on redemande une fois
                    log.warn("Réponse du modèle vision incomplète ({}), nouvel essai", root.getClass().getSimpleName());
                    continue;
                }
                // En WARN : seulement les types d'erreur (jamais le contenu de l'ordonnance, ENF05)
                log.warn("Échec de l'appel au modèle vision ({}): {} (cause : {})",
                        provider, e.getClass().getSimpleName(), root.getClass().getSimpleName());
                // En DEBUG (développement uniquement, données fictives) : détail complet
                log.debug("Détail de l'erreur du modèle vision", e);
                throw PrescriptionException.visionUnavailable(e);
            }
        }
    }

    static Throwable rootCause(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    /** Erreur de lecture du JSON renvoyé par le modèle (réponse tronquée ou mal formée). */
    static boolean isInvalidJson(Throwable root) {
        return root instanceof com.fasterxml.jackson.core.JsonProcessingException;
    }

    @Override
    public String providerName() {
        return provider;
    }
}
