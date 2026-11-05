package ma.medidispo.pharmacy_service.stock;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import ma.medidispo.pharmacy_service.pharmacy.Pharmacy;
import ma.medidispo.pharmacy_service.pharmacy.PharmacyRepository;

@Service
public class StockService {

    private final StockRepository stockRepository;
    private final PharmacyRepository pharmacyRepository;

    public StockService(StockRepository stockRepository,
                        PharmacyRepository pharmacyRepository) {
        this.stockRepository = stockRepository;
        this.pharmacyRepository = pharmacyRepository;
    }

    @Transactional
    public void updateStock(UUID pharmacyId, String medicationCode,
                            StockStatus status, UUID pharmacistId) {

        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Pharmacie introuvable"));

        if (pharmacistId == null ||
            !pharmacistId.equals(pharmacy.getOwnerUserId())) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Acces interdit a cette pharmacie");
        }

        if (status == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Statut obligatoire");
        }

        stockRepository.save(pharmacyId, medicationCode, status);
    }
}