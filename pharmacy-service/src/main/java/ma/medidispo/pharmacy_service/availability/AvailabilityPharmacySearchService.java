package ma.medidispo.pharmacy_service.availability;

import ma.medidispo.pharmacy_service.pharmacy.PharmacyRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AvailabilityPharmacySearchService {

    private final PharmacyRepository pharmacyRepository;

    public AvailabilityPharmacySearchService(PharmacyRepository pharmacyRepository) {
        this.pharmacyRepository = pharmacyRepository;
    }

    public List<PharmacyRepository.NearbyView> findApprovedNearby(
            double latitude, double longitude, int radiusM) {

        if (latitude < -90 || latitude > 90
                || longitude < -180 || longitude > 180
                || radiusM < 100 || radiusM > 20000) {
            throw new IllegalArgumentException("Position ou rayon invalide");
        }

        return pharmacyRepository.findNearby(latitude, longitude, radiusM, 100);
    }
}