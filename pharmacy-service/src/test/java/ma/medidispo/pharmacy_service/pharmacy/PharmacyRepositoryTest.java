package ma.medidispo.pharmacy_service.pharmacy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class PharmacyRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgis = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));

    @Autowired PharmacyRepository repository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM pharmacies");
    }

    private void insert(String name, double lat, double lng, String status) {
        jdbc.update("INSERT INTO pharmacies (name, latitude, longitude, status, source) VALUES (?, ?, ?, ?, 'TEST')",
                name, lat, lng, status);
    }

    @Test
    void returnsApprovedPharmaciesWithinRadiusSortedByDistance() {
        insert("B", 31.6300, -7.9811, "APPROVED");
        insert("A", 31.6270, -7.9880, "APPROVED");
        insert("C loin", 31.6400, -7.9700, "APPROVED");

        List<PharmacyRepository.NearbyView> result = repository.findNearby(31.6258, -7.9891, 2000, 50);

        assertThat(result).extracting(PharmacyRepository.NearbyView::getName).containsExactly("A", "B");
        assertThat(result.get(0).getDistanceM()).isLessThan(result.get(1).getDistanceM());
    }

    @Test
    void hidesPendingPharmacies() {
        insert("Visible", 31.6270, -7.9880, "APPROVED");
        insert("En attente", 31.6262, -7.9889, "PENDING");

        List<PharmacyRepository.NearbyView> result = repository.findNearby(31.6258, -7.9891, 2000, 50);

        assertThat(result).extracting(PharmacyRepository.NearbyView::getName).containsExactly("Visible");
    }

    @Test
    void distanceIsMeasuredInRealMeters() {
        insert("Loin", 31.6295, -8.0089, "APPROVED");

        List<PharmacyRepository.NearbyView> result = repository.findNearby(31.6295, -7.9811, 5000, 50);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDistanceM()).isBetween(2630, 2645);
    }

    @Test
    void respectsTheLimit() {
        insert("P1", 31.6270, -7.9880, "APPROVED");
        insert("P2", 31.6271, -7.9881, "APPROVED");
        insert("P3", 31.6272, -7.9882, "APPROVED");

        assertThat(repository.findNearby(31.6258, -7.9891, 2000, 2)).hasSize(2);
    }
}
