package ma.medidispo.auth_service;

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

/**
 * Sur un vrai PostgreSQL : Flyway crée le schéma, puis Hibernate le valide (ddl-auto=validate).
 * Si une migration ne correspond plus aux entités, le contexte ne démarre pas et ce test échoue.
 */
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@Testcontainers
class FlywayMigrationIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-bookworm"));

    @Autowired JdbcTemplate jdbc;

    @Test void lesMigrationsCreentLeSchemaAttenduParHibernate() {
        var versions = jdbc.queryForList(
            "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);
        assertThat(versions).contains("1", "2");
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.table_constraints WHERE constraint_name = 'fk_refresh_tokens_user'",
            Integer.class)).isEqualTo(1);
    }
}
