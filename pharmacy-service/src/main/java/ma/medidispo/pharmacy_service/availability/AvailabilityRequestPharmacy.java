package ma.medidispo.pharmacy_service.availability;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "availability_request_pharmacies")
@IdClass(AvailabilityRequestPharmacy.Key.class)
public class AvailabilityRequestPharmacy {

    @Id
    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Id
    @Column(name = "pharmacy_id", nullable = false)
    private UUID pharmacyId;

    @Column(nullable = false)
    private String status = "PENDING";

    protected AvailabilityRequestPharmacy() {}

    public AvailabilityRequestPharmacy(UUID requestId, UUID pharmacyId) {
        this.requestId = requestId;
        this.pharmacyId = pharmacyId;
    }

    public UUID getRequestId() { return requestId; }
    public UUID getPharmacyId() { return pharmacyId; }
    public String getStatus() { return status; }

    public static class Key implements Serializable {
        private UUID requestId;
        private UUID pharmacyId;

        public Key() {}

        public Key(UUID requestId, UUID pharmacyId) {
            this.requestId = requestId;
            this.pharmacyId = pharmacyId;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Key other)) return false;
            return java.util.Objects.equals(requestId, other.requestId)
                && java.util.Objects.equals(pharmacyId, other.pharmacyId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(requestId, pharmacyId);
        }
    }
}