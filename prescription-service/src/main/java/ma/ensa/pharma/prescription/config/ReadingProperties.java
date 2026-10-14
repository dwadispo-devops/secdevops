package ma.ensa.pharma.prescription.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Paramètres de la lecture d'ordonnance (préfixe {@code prescription.reading}).
 *
 * @param maxImageSide côté le plus long (en pixels) de l'image envoyée au modèle vision
 * @param maxPixels    nombre maximal de pixels accepté avant décodage (anti "bombe de décompression")
 * @param maxLines     nombre maximal de lignes de médicaments conservées dans la réponse
 */
@Validated
@ConfigurationProperties(prefix = "prescription.reading")
public record ReadingProperties(
        @DefaultValue("2000") @Min(500) int maxImageSide,
        @DefaultValue("40000000") @Min(1_000_000) long maxPixels,
        @DefaultValue("25") @Min(1) int maxLines) {
}
