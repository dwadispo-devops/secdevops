package ma.secdevops.medication.generics;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/** US16 – Requêtes réelles sur PostgreSQL (schéma créé par les migrations Flyway). */
@Testcontainers
class GenericsRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-bookworm");

    static JdbcTemplate jdbc;
    static GenericsRepository repository;

    @BeforeAll
    static void setup() {
        var ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = new JdbcTemplate(ds);
        repository = new GenericsRepository(jdbc);
        jdbc.update("""
                INSERT INTO medication (code, nom, dci1, dosage1, unite_dosage1, dosage_brut, forme, presentation,
                                        ppv, princeps_generique, taux_remboursement) VALUES
                ('6118000160182','AUGMENTIN','AMOXICILLINE / ACIDE CLAVULANIQUE',NULL,'G / MG','1 / 125',
                 'POUDRE POUR SUSPENSION BUVABLE','1 BOITE 16 SACHET',168.20,'P',70),
                ('6118000041177','ZAMOX','amoxicilline / acide clavulanique',NULL,'G / MG','1 / 125',
                 'POUDRE POUR SUSPENSION BUVABLE','1 BOITE 24 SACHET',167.00,'G',70),
                ('6118000040972','DOLIPRANE','PARACETAMOL',1,'G','1','COMPRIME SECABLE','1 BOITE 10 COMPRIME',14,'P',70)
                """);
    }

    @Test
    void readsAllComparisonFields() {
        CatalogRow row = repository.findByCode("6118000160182").orElseThrow();

        assertThat(row.dosageBrut()).isEqualTo("1 / 125");
        assertThat(row.uniteDosage1()).isEqualTo("G / MG");
        assertThat(row.ppv()).isEqualByComparingTo("168.20");
        assertThat(row.tauxRemboursement()).isEqualTo(70);
        assertThat(row.isGeneric()).isFalse();
    }

    @Test
    void sameDciIsCaseInsensitive() {
        assertThat(repository.findBySameDci("AMOXICILLINE / ACIDE CLAVULANIQUE"))
                .extracting(CatalogRow::nom).containsExactly("ZAMOX", "AUGMENTIN");
    }

    @Test
    void endToEndWithServiceOnRealDatabase() {
        GenericsResponse response = new GenericsService(repository).cheaperGenerics("6118000160182", 10);

        assertThat(response.generiques()).extracting(GenericsResponse.CheaperGeneric::nom).containsExactly("ZAMOX");
    }

    @Test
    void parametersAreNeverInterpretedAsSql() {
        assertThat(repository.findByCode("' OR 1=1 --")).isEmpty();
        assertThat(repository.findBySameDci("' OR 1=1 --")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM medication", Integer.class)).isEqualTo(3);
    }
}
