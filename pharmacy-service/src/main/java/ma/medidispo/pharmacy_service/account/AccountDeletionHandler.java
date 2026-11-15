package ma.medidispo.pharmacy_service.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * US20 / loi 09-08 : réagit à l'événement COMPTE_SUPPRIME publié par auth-service.
 * Rejouable sans risque (un deuxième effacement ne trouve plus rien). Aucune donnée personnelle dans les logs.
 */
@Service
public class AccountDeletionHandler {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionHandler.class);

    private final AccountDataRepository repository;

    public AccountDeletionHandler(AccountDataRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void compteSupprime(UUID userId, String role) {
        int lignes = "PHARMACIEN".equals(role)
                ? repository.detacherPharmacien(userId)
                : repository.effacerPatient(userId);
        log.info("Compte supprimé ({}) : {} ligne(s) effacée(s) ou détachée(s)", role, lignes);
    }
}
