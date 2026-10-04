package ma.medidispo.pharmacy_service.pharmacy;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "pharmacies")
public class Pharmacy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "osm_id")
    private Long osmId;

    @Column(nullable = false, length = 200)
    private String name;

    private String address;
    private String phone;

    @Column(name = "opening_hours")
    private String openingHours;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false)
    private String status = "APPROVED";

    @Column(nullable = false)
    private String source = "REGISTERED";

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Pharmacy() {}

    public Pharmacy(String name, String address, String phone, String openingHours,
                    double latitude, double longitude, Long osmId, String source) {
        this.name = name; this.address = address; this.phone = phone;
        this.openingHours = openingHours; this.latitude = latitude;
        this.longitude = longitude; this.osmId = osmId; this.source = source;
    }

    public UUID getId() { return id; }
    public Long getOsmId() { return osmId; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getOpeningHours() { return openingHours; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getStatus() { return status; }
    public String getSource() { return source; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
