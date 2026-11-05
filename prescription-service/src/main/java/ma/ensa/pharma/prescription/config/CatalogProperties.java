package ma.ensa.pharma.prescription.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Accès au catalogue des médicaments (préfixe {@code prescription.catalog}).
 *
 * @param baseUrl    adresse de medication-service
 * @param timeout    délai maximal d'une recherche (le patient ne doit pas attendre, ENF02)
 * @param candidates nombre de candidats demandés au catalogue pour chaque ligne lue
 */
@Validated
@ConfigurationProperties(prefix = "prescription.catalog")
public record CatalogProperties(
        @DefaultValue("http://localhost:8083") String baseUrl,
        @DefaultValue("3s") Duration timeout,
        @DefaultValue("10") @Min(3) @Max(20) int candidates) {
}
