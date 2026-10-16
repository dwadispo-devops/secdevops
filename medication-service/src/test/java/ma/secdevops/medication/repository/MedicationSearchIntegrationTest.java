package ma.secdevops.medication.repository;

import ma.secdevops.medication.dto.MedicationSearchResponse;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration Testcontainers pour la recherche de médicaments avec PostgreSQL 16.
 * Valide l'indexation pg_trgm, la pertinence, la tolérance aux fautes de frappe et la sécurité SQL.
 */
@Testcontainers
class MedicationSearchIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            System.getProperty("test.postgres.image", "postgres:16-bookworm"))
            .withDatabaseName("medication_search_test")
            .withUsername("test_user")
            .withPassword("test_password");

    static JdbcTemplate jdbcTemplate;
    static MedicationSearchRepository repository;

    @BeforeAll
    static void setUpAll() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());

        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new MedicationSearchRepository(jdbcTemplate);

        // Migrations Flyway (V1 crée la table et l'index GIN trigramme)
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @BeforeEach
    void setUpData() {
        jdbcTemplate.execute("TRUNCATE TABLE medication RESTART IDENTITY CASCADE");

        // Jeu de données de test représentatif
        jdbcTemplate.execute("""
            INSERT INTO medication (code, nom, dci1, dosage1, unite_dosage1, forme, presentation, ppv, ph, prix_br, princeps_generique, taux_remboursement)
            VALUES
            ('6118000000001', 'DOLIPRANE 1000 MG', 'PARACETAMOL', 1000, 'MG', 'COMPRIME', '1 BOITE 8 COMPRIME', 45.50, 30.20, 45.50, 'P', 70),
            ('6118000000002', 'DOLIPRANE 500 MG', 'PARACETAMOL', 500, 'MG', 'GELULE', '1 BOITE 16 GELULE', 16.00, 10.00, 16.00, 'P', 70),
            ('6118000000003', 'AMOXICILLINE GNR', 'AMOXICILLINE', 500, 'MG', 'GELULE', '1 BOITE 12 GELULE', 38.00, NULL, 38.00, 'G', 70),
            ('6118000000004', 'CORTANCYL', 'PREDNISONE', 25, 'MG', 'COMPRIME', '1 BOITE 30 COMPRIME', 22.30, 14.80, 22.30, 'G', 0),
            ('6118000000005', 'PANADOL 500 MG', 'PARACETAMOL', 500, 'MG', 'COMPRIME', '1 BOITE 24 COMPRIME', 20.00, 15.00, 20.00, 'P', 70),
            ('6118000000006', 'ZADRYL SIROP', 'CETIRIZINE', 0.5, 'MG/ML', 'SOLUTION BUVABLE', '1 FLACON 60 ML', 31.90, 19.90, 31.90, 'G', 70);
        """);
    }

    @Test
    @DisplayName("Recherche par préfixe : 'dolip' retourne les DOLIPRANE")
    void testSearchByPrefix() {
        List<MedicationSearchResponse> results = repository.search("dolip", 10);

        assertThat(results).isNotEmpty();
        assertThat(results).hasSize(2);
        assertThat(results.get(0).nom()).startsWith("DOLIPRANE");
        assertThat(results.get(1).nom()).startsWith("DOLIPRANE");
    }

    @Test
    @DisplayName("Recherche par sous-chaîne : 'cilline' retourne AMOXICILLINE GNR")
    void testSearchBySubstring() {
        List<MedicationSearchResponse> results = repository.search("cilline", 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).nom()).isEqualTo("AMOXICILLINE GNR");
        assertThat(results.get(0).dci1()).isEqualTo("AMOXICILLINE");
    }

    @Test
    @DisplayName("Tolérance faute de frappe : 'doliprne' retourne DOLIPRANE via similarité trigramme")
    void testSearchTypoTolerance() {
        List<MedicationSearchResponse> results = repository.search("doliprne", 10);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).nom()).startsWith("DOLIPRANE");
    }

    @Test
    @DisplayName("Insensibilité à la casse : 'doliprane', 'DOLIPRANE', 'DoLiP' retournent les mêmes résultats")
    void testSearchCaseInsensitivity() {
        List<MedicationSearchResponse> lower = repository.search("doliprane", 10);
        List<MedicationSearchResponse> upper = repository.search("DOLIPRANE", 10);
        List<MedicationSearchResponse> mixed = repository.search("DoLiPrAnE", 10);

        assertThat(lower).hasSize(2);
        assertThat(upper).hasSize(2);
        assertThat(mixed).hasSize(2);
        assertThat(lower.get(0).code()).isEqualTo(upper.get(0).code());
        assertThat(lower.get(0).code()).isEqualTo(mixed.get(0).code());
    }

    @Test
    @DisplayName("Respect de la limite : limit=1 retourne un seul résultat")
    void testSearchLimitRespected() {
        List<MedicationSearchResponse> results = repository.search("dolip", 1);

        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("Aucun résultat : 'zzzzz' retourne une liste vide sans erreur")
    void testSearchNoResults() {
        List<MedicationSearchResponse> results = repository.search("zzzzz", 10);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Tentative d'injection SQL : '' OR 1=1 --' ne casse rien et retourne une liste vide")
    void testSearchSqlInjectionSafety() {
        List<MedicationSearchResponse> results = repository.search("' OR 1=1 --", 10);

        assertThat(results).isEmpty();
    }
}
