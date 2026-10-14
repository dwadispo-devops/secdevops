package ma.ensa.pharma.prescription.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI prescriptionOpenApi() {
        return new OpenAPI().info(new Info()
                .title("prescription-service")
                .version("0.1.0")
                .description("Lecture d'ordonnances imprimées par un modèle vision (preuve de concept TS05). "
                        + "Les résultats ne sont pas encore rapprochés du catalogue CNOPS (RAG prévu au Sprint 2)."));
    }
}
