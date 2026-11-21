package ma.medidispo.pharmacy_service.admin;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static ma.medidispo.pharmacy_service.admin.PharmacyAdminDtos.PendingPharmacy;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class PharmacyAdminService {
    private final PharmacyAdminRepository repository;

    public PharmacyAdminService(PharmacyAdminRepository repository) {
        this.repository = repository;
    }

    public List<PendingPharmacy> pending() {
        return repository.findPending();
    }

    public PendingPharmacy approve(UUID id) {
        return repository.approvePending(id).orElseThrow(() -> repository.exists(id)
            ? new ResponseStatusException(CONFLICT, "Cette pharmacie n’est plus en attente de validation.")
            : new ResponseStatusException(NOT_FOUND, "Pharmacie introuvable."));
    }
}
