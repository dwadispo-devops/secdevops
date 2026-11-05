package ma.ensa.pharma.prescription.ai;

import com.fasterxml.jackson.core.io.JsonEOFException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Détection d'une réponse JSON coupée par le modèle (cas réel observé avec Gemini sous forte charge). */
class SpringAiVisionReaderTest {

    @Test
    void truncatedJsonIsDetectedAsInvalidJson() {
        RuntimeException wrapped = new RuntimeException("conversion",
                new IllegalStateException("parse", new JsonEOFException(null, null, "fin inattendue")));

        Throwable root = SpringAiVisionReader.rootCause(wrapped);

        assertThat(root).isInstanceOf(JsonEOFException.class);
        assertThat(SpringAiVisionReader.isInvalidJson(root)).isTrue();
    }

    @Test
    void serverErrorsAreNotRetriedAsJsonErrors() {
        RuntimeException serverError = new RuntimeException("503 high demand");

        assertThat(SpringAiVisionReader.isInvalidJson(SpringAiVisionReader.rootCause(serverError))).isFalse();
    }
}
