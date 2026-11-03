package ma.medidispo.pharmacy_service.stock;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class StockRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
        DockerImageName.parse("postgis/postgis:16-3.5")
            .asCompatibleSubstituteFor("postgres"));

    @Autowired
    StockRepository stockRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayAppliesAvailabilityBeforeStockAndOwnership() {
        assertThat(jdbc.queryForList("""
            SELECT version FROM flyway_schema_history
            WHERE success = true AND version IS NOT NULL
            ORDER BY installed_rank
            """, String.class)).containsExactly("1", "2", "3", "4", "5");
    }

    @Test
    void shouldInsertAndUpdateStockWithoutDuplicates() {
        UUID pharmacyId = UUID.randomUUID();
        String medicationCode = "123456";

        jdbc.update("""
            INSERT INTO pharmacies
                (id, name, address, phone, opening_hours,
                 latitude, longitude, license_number, status)
            VALUES (?, ?, 'Adresse test', '0000000000', '09:00-18:00',
                    31.63, -8.0, ?, 'APPROVED')
            """, pharmacyId, "Pharmacie test US11",
            "US11-" + pharmacyId);

        stockRepository.save(
            pharmacyId, medicationCode, StockStatus.AVAILABLE);

        assertThat(jdbc.queryForObject("""
            SELECT status FROM pharmacy_stock
            WHERE pharmacy_id = ? AND medication_code = ?
            """, String.class, pharmacyId, medicationCode))
            .isEqualTo("AVAILABLE");

        stockRepository.save(
            pharmacyId, medicationCode, StockStatus.OUT_OF_STOCK);

        assertThat(jdbc.queryForObject("""
            SELECT status FROM pharmacy_stock
            WHERE pharmacy_id = ? AND medication_code = ?
            """, String.class, pharmacyId, medicationCode))
            .isEqualTo("OUT_OF_STOCK");

        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM pharmacy_stock
            WHERE pharmacy_id = ? AND medication_code = ?
            """, Integer.class, pharmacyId, medicationCode))
            .isEqualTo(1);
    }
}
