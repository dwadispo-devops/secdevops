package ma.medidispo.pharmacy_service.dashboard;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static ma.medidispo.pharmacy_service.dashboard.DashboardDtos.*;

@RestController
@RequestMapping("/api/pharmacies/me")
public class DashboardController {
    private final DashboardRepository repository;

    public DashboardController(DashboardRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(Authentication auth) {
        UUID owner = (UUID) auth.getPrincipal();
        return repository.findByOwner(owner);
    }
}
