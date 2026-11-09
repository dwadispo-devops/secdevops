package ma.secdevops.medication.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class MedicationFinancialIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-bookworm");
    static JdbcTemplate jdbc;
    static MedicationFinancialRepository repository;
    @BeforeAll static void setup() {
        var ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = new JdbcTemplate(ds);
        repository = new MedicationFinancialRepository(jdbc);
        jdbc.update("INSERT INTO medication (code,nom,presentation,ppv,ph,prix_br,taux_remboursement) VALUES ('6118000000001','Produit test','1 BOITE',12.85,9,7,70),('6118000000002','Produit sans prix','1 FLACON',NULL,20,30,NULL)");
    }
    @Test void exactCodeAndFinancialFields() {
        var result = repository.findByCodes(List.of("6118000000001", "6118000000002", "9999999999999"));
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().ppv()).isEqualByComparingTo("12.85");
        assertThat(result.getFirst().tauxRemboursement()).isEqualTo(70);
        assertThat(result.get(1).ppv()).isNull();
        assertThat(result.get(1).tauxRemboursement()).isNull();
    }
    @Test void noFuzzyMatchAndParameterizedSql() {
        assertThat(repository.findByCodes(List.of("Produit test", "611800000000", "' OR 1=1 --"))).isEmpty();
        assertThat(repository.findByCodes(List.of())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM medication", Integer.class)).isEqualTo(2);
    }
}
