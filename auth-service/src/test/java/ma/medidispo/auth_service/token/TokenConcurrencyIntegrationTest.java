package ma.medidispo.auth_service.token;

import ma.medidispo.auth_service.auth.LoginRequest;
import ma.medidispo.auth_service.exception.InvalidRefreshTokenException;
import ma.medidispo.auth_service.user.Role;
import ma.medidispo.auth_service.user.User;
import ma.medidispo.auth_service.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=20",
        "spring.datasource.hikari.connection-init-sql=SET application_name = 'auth_refresh_qa'"
})
@Testcontainers
class TokenConcurrencyIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-bookworm")
            .withDatabaseName("auth_concurrency_test");

    @Autowired TokenService service;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    private User createUser() {
        User user = new User();
        user.setEmail("qa-" + UUID.randomUUID() + "@example.invalid");
        user.setPasswordHash(encoder.encode("FictifQA123"));
        user.setFirstName("Fictif");
        user.setLastName("QA");
        user.setRole(Role.PATIENT);
        return users.saveAndFlush(user);
    }

    @Test
    void concurrentRefreshConsumesOnceAndReplayRevocationCommits() throws Exception {
        User user = createUser();
        String raw = service.login(new LoginRequest(user.getEmail(), "FictifQA123")).refreshToken();
        int contenders = 8;
        ExecutorService executor = Executors.newFixedThreadPool(contenders);
        List<Future<Boolean>> results = new ArrayList<>();
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                users.findByIdForUpdate(user.getId()).orElseThrow();
                for (int i = 0; i < contenders; i++) {
                    results.add(executor.submit(() -> {
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start gate timed out");
                        try {
                            service.refresh(raw);
                            return true;
                        } catch (InvalidRefreshTokenException expected) {
                            return false;
                        }
                    }));
                }
                try {
                    assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                    start.countDown();
                    // Verify actual database contention, rather than relying on thread timing.
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                    int waiting;
                    do {
                        jdbc.execute("SELECT pg_stat_clear_snapshot()");
                        waiting = jdbc.queryForObject("""
                                SELECT count(*) FROM pg_stat_activity
                                WHERE application_name = 'auth_refresh_qa' AND wait_event_type = 'Lock'
                                """, Integer.class);
                        if (waiting < contenders) LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
                    } while (waiting < contenders && System.nanoTime() < deadline);
                    assertThat(waiting).isEqualTo(contenders);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            });
            int successes = 0;
            for (Future<Boolean> result : results) {
                if (result.get(20, TimeUnit.SECONDS)) successes++;
            }
            assertThat(successes).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE user_id = ?",
                    Integer.class, user.getId())).isEqualTo(2);
            // The replay exception must not roll back the revocation of the winner's replacement.
            assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked = false",
                    Integer.class, user.getId())).isZero();
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void logoutAndSequentialReplayKeepTheirTransactionalEffects() {
        User user = createUser();
        var first = service.login(new LoginRequest(user.getEmail(), "FictifQA123"));
        var second = service.refresh(first.refreshToken());
        service.logout(second.refreshToken());
        org.junit.jupiter.api.Assertions.assertThrows(InvalidRefreshTokenException.class,
                () -> service.refresh(second.refreshToken()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked = false",
                Integer.class, user.getId())).isZero();
    }
}
