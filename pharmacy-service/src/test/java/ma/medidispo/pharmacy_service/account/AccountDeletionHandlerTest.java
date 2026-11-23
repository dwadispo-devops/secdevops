package ma.medidispo.pharmacy_service.account;

import ma.medidispo.pharmacy_service.notification.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** US20 / loi 09-08 : effacement des données d'un compte supprimé. */
@SpringBootTest @Testcontainers
class AccountDeletionHandlerTest {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
        DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));

    @Autowired AccountDeletionHandler handler;
    @Autowired NotificationRepository notifications;
    @Autowired JdbcTemplate jdbc;

    UUID pharmacie(UUID owner, String source) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO pharmacies (id,name,latitude,longitude,owner_user_id,status,source) VALUES (?,?,31.63,-8,?,'APPROVED',?)",
            id, "Pharmacie " + id, owner, source);
        return id;
    }
    UUID demande(UUID patient, UUID pharmacy) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO availability_requests (id,patient_id,latitude,longitude,radius_m) VALUES (?,?,31.63,-8,5000)", id, patient);
        jdbc.update("INSERT INTO availability_request_pharmacies (request_id,pharmacy_id) VALUES (?,?)", id, pharmacy);
        return id;
    }
    int compter(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test void effaceLesDemandesEtNotificationsDuPatientSeulement() {
        UUID patient = UUID.randomUUID(), autre = UUID.randomUUID();
        UUID ph = pharmacie(UUID.randomUUID(), "REGISTERED");
        demande(patient, ph); demande(patient, ph); demande(autre, ph);
        notifications.insert(patient, "TEST", "Titre", "Message", "/");
        notifications.insert(autre, "TEST", "Titre", "Message", "/");

        handler.compteSupprime(patient, "PATIENT");

        assertThat(compter("SELECT count(*) FROM availability_requests WHERE patient_id = ?", patient)).isZero();
        assertThat(compter("SELECT count(*) FROM notifications WHERE user_id = ?", patient)).isZero();
        assertThat(compter("SELECT count(*) FROM availability_requests WHERE patient_id = ?", autre)).isEqualTo(1);
        assertThat(compter("SELECT count(*) FROM notifications WHERE user_id = ?", autre)).isEqualTo(1);

        handler.compteSupprime(patient, "PATIENT"); // rejouable sans erreur
    }

    @Test void detacheLaPharmacieDuPharmacienEtLaRendInvisible() {
        UUID pharmacien = UUID.randomUUID();
        UUID inscrite = pharmacie(pharmacien, "REGISTERED");
        UUID osm = pharmacie(pharmacien, "OSM");
        notifications.insert(pharmacien, "TEST", "Titre", "Message", "/");

        handler.compteSupprime(pharmacien, "PHARMACIEN");

        assertThat(compter("SELECT count(*) FROM pharmacies WHERE owner_user_id = ?", pharmacien)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM pharmacies WHERE id = ?", String.class, inscrite)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM pharmacies WHERE id = ?", String.class, osm)).isEqualTo("UNVERIFIED");
        assertThat(compter("SELECT count(*) FROM notifications WHERE user_id = ?", pharmacien)).isZero();
    }
}
