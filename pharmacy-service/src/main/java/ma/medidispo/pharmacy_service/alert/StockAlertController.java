package ma.medidispo.pharmacy_service.alert;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static ma.medidispo.pharmacy_service.alert.StockAlertDtos.*;

/** US22 : alertes du patient connecté (rôle PATIENT). L'identifiant vient du JWT. */
@RestController
@RequestMapping("/api/alertes")
public class StockAlertController {

    private final StockAlertService service;

    public StockAlertController(StockAlertService service) { this.service = service; }

    @GetMapping("/me")
    public List<StockAlert> mesAlertes(Authentication auth) {
        return service.mesAlertes(patient(auth));
    }

    @PostMapping
    @ResponseStatus(CREATED)
    public StockAlert creer(Authentication auth, @Valid @RequestBody NewAlert alerte) {
        return service.creer(patient(auth), alerte);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(NO_CONTENT)
    public void supprimer(Authentication auth, @PathVariable UUID id) {
        service.supprimer(patient(auth), id);
    }

    private static UUID patient(Authentication auth) {
        return UUID.fromString(auth.getName());
    }
}
