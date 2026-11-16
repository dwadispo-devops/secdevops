package ma.medidispo.pharmacy_service.alert;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.*;
import static ma.medidispo.pharmacy_service.alert.StockAlertDtos.*;

/** US22 / EF25 : le patient gère ses alertes de retour en stock. */
@Service
public class StockAlertService {

    static final int MAX_ACTIVES = 10;
    static final int RAYON_DEFAUT_M = 5000;

    private final StockAlertRepository repository;

    public StockAlertService(StockAlertRepository repository) { this.repository = repository; }

    public List<StockAlert> mesAlertes(UUID patientId) {
        return repository.list(patientId);
    }

    @Transactional
    public StockAlert creer(UUID patientId, NewAlert alerte) {
        if (repository.countActive(patientId) >= MAX_ACTIVES) {
            throw new ResponseStatusException(BAD_REQUEST, "Vous avez déjà " + MAX_ACTIVES + " alertes actives.");
        }
        int rayon = alerte.radiusM() == null ? RAYON_DEFAUT_M : alerte.radiusM();
        try {
            UUID id = repository.insert(patientId, alerte, rayon);
            return repository.list(patientId).stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow();
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(CONFLICT, "Vous avez déjà une alerte active pour ce médicament.");
        }
    }

    public void supprimer(UUID patientId, UUID alertId) {
        if (repository.delete(patientId, alertId) == 0) {
            throw new ResponseStatusException(NOT_FOUND, "Alerte introuvable.");
        }
    }
}
