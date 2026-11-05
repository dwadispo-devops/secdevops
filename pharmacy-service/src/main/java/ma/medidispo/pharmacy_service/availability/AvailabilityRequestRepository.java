package ma.medidispo.pharmacy_service.availability;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AvailabilityRequestRepository extends JpaRepository<AvailabilityRequest, UUID> {
}