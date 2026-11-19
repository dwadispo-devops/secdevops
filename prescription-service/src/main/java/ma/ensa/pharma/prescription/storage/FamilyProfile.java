package ma.ensa.pharma.prescription.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "family_profile")
public class FamilyProfile {
    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(nullable = false, length = 60)
    private String label;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected FamilyProfile() {}

    public FamilyProfile(UUID patientId, String label) {
        this.id = UUID.randomUUID();
        this.patientId = patientId;
        this.label = label;
        this.createdAt = Instant.now();
    }

    public void rename(String label) { this.label = label; }
    public void hide() { this.deletedAt = Instant.now(); }
    public boolean belongsTo(UUID patient) { return patientId.equals(patient); }
    public boolean isActive() { return deletedAt == null; }
    public UUID getId() { return id; }
    public UUID getPatientId() { return patientId; }
    public String getLabel() { return label; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
