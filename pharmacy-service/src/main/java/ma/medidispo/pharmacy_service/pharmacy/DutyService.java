package ma.medidispo.pharmacy_service.pharmacy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Service
public class DutyService {

    static final ZoneId MOROCCO = ZoneId.of("Africa/Casablanca");

    private final DutyRepository repository;
    private final Clock clock;

    @Autowired
    public DutyService(DutyRepository repository) {
        this(repository, Clock.system(MOROCCO));
    }

    DutyService(DutyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public DutyResponse onDutyToday(Double lat, Double lng) {
        LocalDate today = LocalDate.now(clock);
        boolean located = lat != null && lng != null;

        List<DutyPharmacy> list = repository.findOnDuty(today).stream()
                .map(v -> new DutyPharmacy(
                        v.getId(), v.getName(), v.getAddress(), v.getPhone(), v.getOpeningHours(),
                        v.getLatitude(), v.getLongitude(),
                        located ? distanceM(lat, lng, v.getLatitude(), v.getLongitude()) : null,
                        v.getDutyUntil()))
                .toList();

        if (located) {
            list = list.stream()
                    .sorted(Comparator.comparing(DutyPharmacy::distanceM))
                    .toList();
        }
        return new DutyResponse(today.toString(), list);
    }

    /** Distance à vol d'oiseau (haversine), en mètres. */
    static int distanceM(double lat1, double lng1, double lat2, double lng2) {
        double r = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return (int) Math.round(2 * r * Math.asin(Math.sqrt(a)));
    }
}
