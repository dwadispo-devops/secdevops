package ma.secdevops.medication.importer;

import ma.secdevops.medication.model.Medication;
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

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test d'intégration de l'import du référentiel médicaments.
 * <p>
 * Utilise Testcontainers pour démarrer un vrai PostgreSQL 16 isolé.
 * Le schéma est créé par Flyway (mêmes migrations que la production).
 * Les données viennent d'un petit CSV de test avec des cas limites :
 * - Prix à 0 (→ NULL)
 * - Doublon de CODE (→ upsert, pas d'erreur)
 * - Ligne sans NOM (→ ignorée)
 * - CODE invalide (→ ignorée)
 * - DOSAGE1 avec virgule décimale (→ converti)
 * </p>
 */
@Testcontainers
class MedicationImportIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("medication_db_test")
            .withUsername("test_user")
            .withPassword("test_password");

    static JdbcTemplate jdbcTemplate;
    static MedicationRepository repository;

    @BeforeAll
    static void setUpAll() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());

        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new MedicationRepository(jdbcTemplate);

        // Appliquer les migrations Flyway (même schéma que la prod)
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @BeforeEach
    void setUp() {
        // Vider la table entre chaque test
        jdbcTemplate.execute("TRUNCATE TABLE medication RESTART IDENTITY CASCADE");
    }

    // =========================================================================
    // Test 1 : Import de base — vérifier les valeurs parsées
    // =========================================================================

    @Test
    @DisplayName("Import CSV : les médicaments valides sont insérés avec les bonnes valeurs")
    void testImportBasic() throws Exception {
        // Given
        ImportResult result = new ImportResult();
        List<Medication> medications = readCsv("data/test-medications.csv", result);

        // When
        int affected = repository.upsertAll(medications, 100);

        // Then — vérifier le nombre de médicaments en base
        // Le CSV a 9 lignes de données :
        //   - 1 doublon de CODE (6118000000001 apparaît 2 fois) → 1 seule en base
        //   - 1 ligne sans NOM (SANS NOM) → ignorée par le lecteur
        //   - 1 CODE invalide (INVALIDE) → ignorée par le lecteur
        // => 6 médicaments distincts attendus en base
        assertThat(repository.count()).isEqualTo(6);
    }

    // =========================================================================
    // Test 2 : Lignes ignorées (code invalide, nom absent)
    // =========================================================================

    @Test
    @DisplayName("Import CSV : les lignes invalides sont ignorées et comptées")
    void testIgnoredLines() throws Exception {
        ImportResult result = new ImportResult();
        readCsv("data/test-medications.csv", result);

        // 2 lignes ignorées : CODE invalide + NOM absent
        assertThat(result.getIgnoredCount()).isEqualTo(2);
        // Vérifier que les raisons sont loguées
        assertThat(result.getIgnoredReasons()).hasSize(2);
    }

    // =========================================================================
    // Test 3 : PPV = 0 → NULL
    // =========================================================================

    @Test
    @DisplayName("Import CSV : PPV = 0 est converti en NULL")
    void testPpvZeroIsNull() throws Exception {
        ImportResult result = new ImportResult();
        List<Medication> medications = readCsv("data/test-medications.csv", result);
        repository.upsertAll(medications, 100);

        // PRODUIT HOSPITALIER (code 6118000000003) : PPV=0 → NULL
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT ppv, ph, prix_br FROM medication WHERE code = ?",
                "6118000000003"
        );
        assertThat(row.get("ppv")).isNull();       // PPV 0 → NULL
        assertThat(row.get("ph")).isNotNull();      // PH 150 → 150
        assertThat(row.get("prix_br")).isNull();    // PRIX_BR 0 → NULL
    }

    // =========================================================================
    // Test 4 : PH = 0 → NULL
    // =========================================================================

    @Test
    @DisplayName("Import CSV : PH = 0 est converti en NULL")
    void testPhZeroIsNull() throws Exception {
        ImportResult result = new ImportResult();
        List<Medication> medications = readCsv("data/test-medications.csv", result);
        repository.upsertAll(medications, 100);

        // AMOXICILLINE GNR (code 6118000000002) : PH=0 → NULL
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT ppv, ph FROM medication WHERE code = ?",
                "6118000000002"
        );
        assertThat(row.get("ph")).isNull();         // PH 0 → NULL
        assertThat(row.get("ppv")).isNotNull();     // PPV 38 → 38.00
    }

    // =========================================================================
    // Test 5 : Idempotence (doublon de CODE → upsert, pas d'erreur)
    // =========================================================================

    @Test
    @DisplayName("Import CSV : doublon de CODE → upsert sans erreur (idempotence)")
    void testDuplicateCodeIsUpserted() throws Exception {
        ImportResult result = new ImportResult();
        List<Medication> medications = readCsv("data/test-medications.csv", result);

        // Premier import
        repository.upsertAll(medications, 100);
        int countAfterFirst = repository.count();

        // Deuxième import (même données)
        repository.upsertAll(medications, 100);
        int countAfterSecond = repository.count();

        // Le nombre de médicaments ne doit pas changer
        assertThat(countAfterSecond).isEqualTo(countAfterFirst);
        assertThat(countAfterFirst).isEqualTo(6);
    }

    // =========================================================================
    // Test 6 : DOSAGE1 avec virgule décimale française
    // =========================================================================

    @Test
    @DisplayName("Import CSV : DOSAGE1 avec virgule décimale est converti correctement")
    void testDosageWithComma() throws Exception {
        ImportResult result = new ImportResult();
        List<Medication> medications = readCsv("data/test-medications.csv", result);
        repository.upsertAll(medications, 100);

        // ZADRYL SIROP (code 6118000000006) : DOSAGE1="0,5" → 0.5
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT dosage1 FROM medication WHERE code = ?",
                "6118000000006"
        );
        BigDecimal dosage = (BigDecimal) row.get("dosage1");
        assertThat(dosage).isNotNull();
        assertThat(dosage.compareTo(new BigDecimal("0.5"))).isZero();
    }

    // =========================================================================
    // Test 7 : Taux de remboursement parsé correctement
    // =========================================================================

    @Test
    @DisplayName("Import CSV : TAUX_REMBOURSEMENT '70%' → 70, '0%' → 0")
    void testTauxRemboursement() throws Exception {
        ImportResult result = new ImportResult();
        List<Medication> medications = readCsv("data/test-medications.csv", result);
        repository.upsertAll(medications, 100);

        // DOLIPRANE (70%) et CORTANCYL (0%)
        Map<String, Object> doliprane = jdbcTemplate.queryForMap(
                "SELECT taux_remboursement FROM medication WHERE code = ?",
                "6118000000001"
        );
        Map<String, Object> cortancyl = jdbcTemplate.queryForMap(
                "SELECT taux_remboursement FROM medication WHERE code = ?",
                "6118000000004"
        );
        assertThat(((Number) doliprane.get("taux_remboursement")).intValue()).isEqualTo(70);
        assertThat(((Number) cortancyl.get("taux_remboursement")).intValue()).isEqualTo(0);
    }

    // =========================================================================
    // Test 8 : Valeurs texte du DOLIPRANE
    // =========================================================================

    @Test
    @DisplayName("Import CSV : les valeurs texte sont importées correctement (DOLIPRANE)")
    void testDolipraneParsed() throws Exception {
        ImportResult result = new ImportResult();
        List<Medication> medications = readCsv("data/test-medications.csv", result);
        repository.upsertAll(medications, 100);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT nom, dci1, forme, princeps_generique FROM medication WHERE code = ?",
                "6118000000001"
        );
        assertThat(row.get("nom")).isEqualTo("DOLIPRANE 1000 MG");
        assertThat(row.get("dci1")).isEqualTo("PARACETAMOL");
        assertThat(row.get("forme")).isEqualTo("COMPRIME");
        assertThat(row.get("princeps_generique")).isEqualTo("P");
    }

    // =========================================================================
    // Utilitaire
    // =========================================================================

    private List<Medication> readCsv(String classpathPath, ImportResult result) throws Exception {
        MedicationCsvReader csvReader = new MedicationCsvReader();
        InputStream is = getClass().getClassLoader().getResourceAsStream(classpathPath);
        assertThat(is).as("Le fichier CSV de test doit exister dans le classpath : " + classpathPath)
                .isNotNull();
        return csvReader.read(is, result);
    }
}
