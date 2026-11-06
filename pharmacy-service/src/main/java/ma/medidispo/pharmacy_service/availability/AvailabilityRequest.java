package ma.medidispo.pharmacy_service.availability;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "availability_requests")
public class AvailabilityRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "radius_m", nullable = false)
    private int radiusM;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected AvailabilityRequest() {}

    public AvailabilityRequest(UUID patientId, double latitude, double longitude, int radiusM) {
        this.patientId = patientId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusM = radiusM;
    }

    public UUID getId() { return id; }
    public UUID getPatientId() { return patientId; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public int getRadiusM() { return radiusM; }
    public String getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
