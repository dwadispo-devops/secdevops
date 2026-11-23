package ma.secdevops.medication.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * TS07 : documentation OpenAPI de medication-service.
 * Swagger UI du service : /swagger-ui.html ; page unique de tous les services : gateway /swagger-ui.html.
 * Serveur "/" : les essais (Try it out) partent de la page courante, donc de la gateway.
 * Schéma bearerAuth : bouton Authorize, on y colle le JWT obtenu à la connexion.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI dwadispoOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("DwaDispo - Catalogue des medicaments")
                .version("v1")
                .description("Recherche dans le catalogue officiel CNOPS et generiques (US16)."))
            .addServersItem(new Server().url("/").description("Passerelle api-gateway"))
            .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                .description("Jeton JWT obtenu à la connexion")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
