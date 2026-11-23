package ma.ensa.pharma.prescription.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentation OpenAPI de prescription-service (mise à jour TS07).
 * Serveur "/" : les essais (Try it out) passent par la gateway. Schéma bearerAuth : bouton Authorize (JWT).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI prescriptionOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("DwaDispo - Ordonnances")
                .version("v1")
                .description("Lecture d'ordonnance par un modèle vision, rapprochement avec le catalogue CNOPS (RAG), "
                    + "historique des ordonnances et profils famille (US06, US07, US18, US24)."))
            .addServersItem(new Server().url("/").description("Passerelle api-gateway"))
            .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                .description("Jeton JWT obtenu à la connexion")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
