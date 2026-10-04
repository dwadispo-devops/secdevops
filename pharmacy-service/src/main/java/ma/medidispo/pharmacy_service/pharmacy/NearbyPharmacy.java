package ma.medidispo.pharmacy_service.pharmacy;

import java.util.UUID;

public record NearbyPharmacy(UUID id, String name, String address, String phone,
                             String openingHours, double latitude, double longitude, int distanceM) {

    static NearbyPharmacy from(PharmacyRepository.NearbyView v) {
        return new NearbyPharmacy(v.getId(), v.getName(), v.getAddress(), v.getPhone(),
                v.getOpeningHours(), v.getLatitude(), v.getLongitude(), v.getDistanceM());
    }
}
