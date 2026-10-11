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

    @Override
    public VisionExtraction read(byte[] png) {
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
            // On ne journalise que le type d'erreur : jamais le contenu de l'ordonnance (ENF05)
            log.warn("Échec de l'appel au modèle vision ({}): {}", provider, e.getClass().getSimpleName());
            throw PrescriptionException.visionUnavailable(e);
        }
    }

    @Override
    public String providerName() {
        return provider;
    }
}
