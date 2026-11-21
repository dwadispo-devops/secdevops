package ma.medidispo.pharmacy_service.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ma.medidispo.pharmacy_service.admin.PharmacyAdminDtos.PendingPharmacy;

@RestController
@RequestMapping("/api/pharmacies/admin")
public class PharmacyAdminController {
    private final PharmacyAdminService service;

    public PharmacyAdminController(PharmacyAdminService service) {
        this.service = service;
    }

    @GetMapping("/pending")
    public List<PendingPharmacy> pending() {
        return service.pending();
    }

    @PostMapping("/{id}/validate")
    public PendingPharmacy validate(@PathVariable UUID id) {
        return service.approve(id);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> error(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of(
            "message", exception.getReason() == null ? "Erreur" : exception.getReason()));
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> invalidId() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Identifiant invalide."));
    }
}
