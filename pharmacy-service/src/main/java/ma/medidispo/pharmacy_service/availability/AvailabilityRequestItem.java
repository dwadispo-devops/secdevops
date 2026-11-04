package ma.medidispo.pharmacy_service.availability;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "availability_request_items")
public class AvailabilityRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "medication_name", nullable = false, length = 200)
    private String medicationName;

    @Column(nullable = false)
    private int quantity;

    protected AvailabilityRequestItem() {}

    public AvailabilityRequestItem(UUID requestId, String medicationName, int quantity) {
        this.requestId = requestId;
        this.medicationName = medicationName;
        this.quantity = quantity;
    }

    public UUID getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public String getMedicationName() { return medicationName; }
    public int getQuantity() { return quantity; }
}