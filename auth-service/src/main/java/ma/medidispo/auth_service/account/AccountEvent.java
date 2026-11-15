package ma.medidispo.auth_service.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import ma.medidispo.auth_service.user.Role;

import java.time.Instant;
import java.util.UUID;

/** US20 : événement de compte en attente de publication dans Kafka (outbox). */
@Entity
@Table(name = "account_events")
public class AccountEvent {

    public static final String COMPTE_SUPPRIME = "COMPTE_SUPPRIME";

    @Id
    private UUID id;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected AccountEvent() {
    }

    public static AccountEvent compteSupprime(UUID userId, Role role) {
        AccountEvent e = new AccountEvent();
        e.id = UUID.randomUUID();
        e.type = COMPTE_SUPPRIME;
        e.userId = userId;
        e.role = role.name();
        e.createdAt = Instant.now();
        return e;
    }

    public void markPublished() { this.publishedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getType() { return type; }
    public UUID getUserId() { return userId; }
    public String getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
}
