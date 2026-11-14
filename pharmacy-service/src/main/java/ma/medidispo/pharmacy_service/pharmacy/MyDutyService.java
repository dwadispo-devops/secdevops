package ma.medidispo.pharmacy_service.pharmacy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import static org.springframework.http.HttpStatus.*;
import static ma.medidispo.pharmacy_service.pharmacy.MyDutyDtos.*;

/**
 * US15 (amélioration) : le pharmacien déclare les périodes de garde de ses pharmacies.
 * Règles : fin >= début, période pas déjà terminée, 31 jours maximum, pas de chevauchement.
 */
@Service
public class MyDutyService {

    static final int MAX_JOURS = 31;

    private final MyDutyRepository repository;
    private final Clock clock;

    @Autowired
    public MyDutyService(MyDutyRepository repository) {
        this(repository, Clock.system(DutyService.MOROCCO));
    }

    MyDutyService(MyDutyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public List<MyDuty> mesGardes(UUID owner) {
        return repository.list(owner);
    }

    @Transactional
    public MyDuty declarer(UUID owner, NewDuty garde) {
        LocalDate aujourdhui = LocalDate.now(clock);
        if (garde.endsOn().isBefore(garde.startsOn())) {
            throw new ResponseStatusException(BAD_REQUEST, "La date de fin doit être égale ou postérieure à la date de début.");
        }
        if (garde.endsOn().isBefore(aujourdhui)) {
            throw new ResponseStatusException(BAD_REQUEST, "Cette période de garde est déjà terminée.");
        }
        if (ChronoUnit.DAYS.between(garde.startsOn(), garde.endsOn()) + 1 > MAX_JOURS) {
            throw new ResponseStatusException(BAD_REQUEST, "Une période de garde ne peut pas dépasser " + MAX_JOURS + " jours.");
        }
        if (!repository.owns(owner, garde.pharmacyId())) {
            throw new ResponseStatusException(NOT_FOUND, "Pharmacie introuvable.");
        }
        if (repository.overlaps(garde.pharmacyId(), garde.startsOn(), garde.endsOn())) {
            throw new ResponseStatusException(CONFLICT, "Cette pharmacie a déjà une garde sur cette période.");
        }
        return repository.insert(garde.pharmacyId(), garde.startsOn(), garde.endsOn());
    }

    public void supprimer(UUID owner, UUID dutyId) {
        if (repository.delete(owner, dutyId) == 0) {
            throw new ResponseStatusException(NOT_FOUND, "Garde introuvable.");
        }
    }
}
