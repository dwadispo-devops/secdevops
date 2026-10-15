package ma.secdevops.medication.importer;

import ma.secdevops.medication.model.Medication;
import ma.secdevops.medication.repository.MedicationSearchRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MedicationCnopsIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            System.getProperty("test.postgres.image", "postgres:16-bookworm"))
            .withDatabaseName("cnops_isolated");
    static JdbcTemplate jdbc;
    static MedicationRepository repository;
    static Flyway flyway;

    @BeforeAll
    static void setup() {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(source);
        repository = new MedicationRepository(jdbc);
        // Exercise the upgrade path from an already deployed V1 schema.
        Flyway.configure().dataSource(source).target("1").load().migrate();
        jdbc.update("INSERT INTO medication(code, nom, dosage1) VALUES (?, ?, ?)",
                "0000000000000", "QA LEGACY", 200);
        flyway = Flyway.configure().dataSource(source).load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT dosage1 FROM medication WHERE code='0000000000000'",
                java.math.BigDecimal.class)).isEqualByComparingTo("200");
        assertThat(jdbc.queryForObject("SELECT dosage_brut FROM medication WHERE code='0000000000000'", String.class)).isNull();
        jdbc.update("DELETE FROM medication WHERE code='0000000000000'");
    }

    @Test
    void realWorkbookSurvivesTwoImportsWithoutLoss() throws Exception {
        var result = new ImportResult();
        var medications = new MedicationExcelReader().read("data/ref-des-medicaments-cnops-2014.xlsx", result);
        assertThat(result.getLinesRead()).isEqualTo(5917);
        assertThat(result.getIgnoredCount()).isZero();
        assertThat(medications).hasSize(5917);
        assertThat(repository.upsertAll(medications, 500)).isEqualTo(5917);
        assertCounts();
        assertEveryDosage(medications);
        assertSourceText();
        var first = jdbc.queryForList("SELECT * FROM medication ORDER BY code");
        assertThat(repository.upsertAll(medications, 500)).isEqualTo(5917);
        assertCounts();
        assertEveryDosage(medications);
        assertSourceText();
        var second = jdbc.queryForList("SELECT * FROM medication ORDER BY code");
        first.forEach(row -> row.remove("updated_at"));
        second.forEach(row -> row.remove("updated_at"));
        assertThat(second).isEqualTo(first);
        flyway.validate();
        System.out.println("CNOPS VERIFIED twice: total=5917 distinct=5917 raw=5736 numeric=4975 compound=761 absent=181 duplicates=0; all source dosages and persisted fields unchanged (except updated_at)");

        // Exercise a changed source value, not just a repeated identical upsert.
        Medication old = medications.stream().filter(m -> m.dosage1() != null).findFirst().orElseThrow();
        Medication changed = new Medication(old.code(), old.nom(), old.dci1(), null, old.uniteDosage1(),
                old.forme(), old.presentation(), old.ppv(), old.ph(), old.prixBr(),
                old.princepsGenerique(), old.tauxRemboursement(), "600 / 5");
        repository.upsertAll(List.of(changed), 1);
        assertThat(jdbc.queryForObject("SELECT dosage_brut FROM medication WHERE code=?", String.class, old.code())).isEqualTo("600 / 5");
        assertThat(jdbc.queryForObject("SELECT dosage1 FROM medication WHERE code=?", java.math.BigDecimal.class, old.code())).isNull();
        repository.upsertAll(List.of(old), 1);
        assertCounts();
        assertEveryDosage(medications);
    }

    private void assertEveryDosage(List<Medication> medications) {
        var rows = jdbc.queryForList("SELECT code, dosage1, dosage_brut FROM medication");
        var byCode = new java.util.HashMap<String, Medication>();
        medications.forEach(m -> byCode.put(m.code(), m));
        for (var row : rows) {
            var expected = byCode.get(row.get("code"));
            assertThat(row.get("dosage_brut")).isEqualTo(expected.dosageBrut());
            var number = (java.math.BigDecimal) row.get("dosage1");
            if (expected.dosage1() == null) assertThat(number).isNull();
            // Preserve the existing NUMERIC(12,3) behavior; full precision remains in dosage_brut.
            else assertThat(number).isEqualByComparingTo(expected.dosage1().setScale(3, java.math.RoundingMode.HALF_UP));
        }
        var search = new MedicationSearchRepository(jdbc);
        var compound = medications.stream().filter(m -> m.dosage1() == null && m.dosageBrut() != null)
                .filter(m -> m.nom().length() <= 50).findFirst().orElseThrow();
        assertThat(search.search(compound.nom(), 20)).anySatisfy(item -> {
            assertThat(item.code()).isEqualTo(compound.code());
            assertThat(item.dosageBrut()).isEqualTo(compound.dosageBrut());
            assertThat(item.dosage1()).isNull();
        });
    }

    private void assertCounts() {
        assertThat(repository.count()).isEqualTo(5917);
        count("COUNT(DISTINCT code)", "TRUE", 5917);
        count("COUNT(*)", "dosage_brut IS NOT NULL", 5736);
        count("COUNT(*)", "dosage1 IS NOT NULL", 4975);
        count("COUNT(*)", "dosage1 IS NULL AND dosage_brut IS NOT NULL", 761);
        count("COUNT(*)", "dosage1 IS NULL AND dosage_brut IS NULL", 181);
        count("COUNT(*) - COUNT(DISTINCT code)", "TRUE", 0);
    }

    private void assertSourceText() throws Exception {
        var rawByCode = new java.util.HashMap<String, String>();
        jdbc.query("SELECT code, dosage_brut FROM medication", (org.springframework.jdbc.core.RowCallbackHandler)
                rs -> rawByCode.put(rs.getString("code"), rs.getString("dosage_brut")));
        try (var input = getClass().getResourceAsStream("/data/ref-des-medicaments-cnops-2014.xlsx");
             var workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(input)) {
            var sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                var row = sheet.getRow(i);
                String code = sourceText(row.getCell(0));
                String dosage = sourceText(row.getCell(3));
                if (dosage != null && dosage.isBlank()) dosage = null;
                assertThat(rawByCode).containsKey(code);
                assertThat(rawByCode.get(code)).as("Excel row %s", i + 1).isEqualTo(dosage);
            }
        }
    }

    private String sourceText(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null || cell.getCellType() == org.apache.poi.ss.usermodel.CellType.BLANK) return null;
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) return cell.getStringCellValue();
        return java.math.BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
    }

    private void count(String expression, String condition, int expected) {
        assertThat(jdbc.queryForObject("SELECT " + expression + " FROM medication WHERE " + condition, Integer.class))
                .isEqualTo(expected);
    }
}
