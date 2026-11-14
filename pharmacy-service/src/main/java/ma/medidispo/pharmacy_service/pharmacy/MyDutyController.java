package ma.medidispo.pharmacy_service.pharmacy;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static ma.medidispo.pharmacy_service.pharmacy.MyDutyDtos.*;

/** US15 (amélioration) : gestion des gardes par le pharmacien (rôle PHARMACIEN, ses pharmacies seulement). */
@RestController
@RequestMapping("/api/pharmacies/me/gardes")
public class MyDutyController {

    private final MyDutyService service;

    public MyDutyController(MyDutyService service) { this.service = service; }

    @GetMapping
    public List<MyDuty> mesGardes(Authentication auth) {
        return service.mesGardes(owner(auth));
    }

    @PostMapping
    @ResponseStatus(CREATED)
    public MyDuty declarer(Authentication auth, @Valid @RequestBody NewDuty garde) {
        return service.declarer(owner(auth), garde);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(NO_CONTENT)
    public void supprimer(Authentication auth, @PathVariable UUID id) {
        service.supprimer(owner(auth), id);
    }

    /** L'identifiant du pharmacien vient du JWT, jamais de la requête. */
    private static UUID owner(Authentication auth) {
        return UUID.fromString(auth.getName());
    }
}
