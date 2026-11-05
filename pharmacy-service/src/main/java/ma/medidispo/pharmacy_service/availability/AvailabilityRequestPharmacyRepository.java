package ma.medidispo.pharmacy_service.availability;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface AvailabilityRequestPharmacyRepository
        extends JpaRepository<AvailabilityRequestPharmacy, AvailabilityRequestPharmacy.Key> {

    List<AvailabilityRequestPharmacy> findByRequestId(UUID requestId);
}