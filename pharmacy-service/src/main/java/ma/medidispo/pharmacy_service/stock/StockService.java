package ma.medidispo.pharmacy_service.stock;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import ma.medidispo.pharmacy_service.alert.StockAlertDtos.StockAvailableEvent;
import ma.medidispo.pharmacy_service.pharmacy.Pharmacy;
import ma.medidispo.pharmacy_service.pharmacy.PharmacyRepository;

@Service
public class StockService {

    private final StockRepository stockRepository;
    private final PharmacyRepository pharmacyRepository;
    private final ApplicationEventPublisher events;

    /** Conservé pour les tests unitaires existants : aucun événement publié. */
    public StockService(StockRepository stockRepository,
                        PharmacyRepository pharmacyRepository) {
        this(stockRepository, pharmacyRepository, event -> { });
    }

    @Autowired
    public StockService(StockRepository stockRepository,
                        PharmacyRepository pharmacyRepository,
                        ApplicationEventPublisher events) {
        this.stockRepository = stockRepository;
        this.pharmacyRepository = pharmacyRepository;
        this.events = events;
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

        StockStatus avant = stockRepository.currentStatus(pharmacyId, medicationCode);
        stockRepository.save(pharmacyId, medicationCode, status);

        // US22 : seulement au passage « pas en stock » → « en stock »
        if (status == StockStatus.AVAILABLE && avant != StockStatus.AVAILABLE) {
            events.publishEvent(StockAvailableEvent.of(pharmacyId, medicationCode));
        }
    }
}
