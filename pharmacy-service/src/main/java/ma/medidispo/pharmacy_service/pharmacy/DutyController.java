package ma.medidispo.pharmacy_service.pharmacy;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** US15 : pharmacies de garde du jour (public, comme /nearby). */
@RestController
@RequestMapping("/api/pharmacies")
public class DutyController {

    private final DutyService service;

    public DutyController(DutyService service) {
        this.service = service;
    }

    @GetMapping("/garde")
    public DutyResponse onDuty(
            @RequestParam(required = false) @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @RequestParam(required = false) @DecimalMin("-180.0") @DecimalMax("180.0") Double lng) {
        if ((lat == null) != (lng == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lat et lng vont ensemble.");
        }
        return service.onDutyToday(lat, lng);
    }
}
