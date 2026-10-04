package ma.medidispo.pharmacy_service.pharmacy;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pharmacies")
public class PharmacyController {

    private final PharmacyRepository repository;

    public PharmacyController(PharmacyRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/nearby")
    public List<NearbyPharmacy> nearby(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lng,
            @RequestParam(defaultValue = "2000") @Min(100) @Max(20000) int radius,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return repository.findNearby(lat, lng, radius, limit).stream()
                .map(NearbyPharmacy::from).toList();
    }
}
