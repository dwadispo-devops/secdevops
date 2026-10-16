package ma.secdevops.medication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée du microservice medication-service.
 * <p>
 * En mode normal : démarre le serveur web (port 8080).
 * Avec le profil "import" : lance l'import du référentiel CNOPS puis s'arrête.
 * </p>
 */
@SpringBootApplication
public class MedicationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedicationServiceApplication.class, args);
    }
}
