package ma.medidispo.pharmacy_service.alert;

import ma.medidispo.pharmacy_service.notification.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static ma.medidispo.pharmacy_service.alert.StockAlertDtos.StockAvailableEvent;

/**
 * US22 : un médicament est revenu en stock dans une pharmacie → les patients dont l'alerte couvre
 * cette pharmacie sont prévenus (cloche + e-mail via l'outbox US14). Rejouable : une alerte déclenchée
 * ne l'est plus. ENF05 : le message ne cite pas le médicament.
 */
@Service
public class StockAlertTrigger {

    private static final Logger log = LoggerFactory.getLogger(StockAlertTrigger.class);

    private final StockAlertRepository alerts;
    private final NotificationRepository notifications;

    public StockAlertTrigger(StockAlertRepository alerts, NotificationRepository notifications) {
        this.alerts = alerts;
        this.notifications = notifications;
    }

    @Transactional
    public void traiter(StockAvailableEvent event) {
        List<UUID> patients = alerts.trigger(event.pharmacyId(), event.medicationCode());
        for (UUID patient : patients) {
            notifications.insert(patient, "STOCK_DISPONIBLE", "Médicament de nouveau disponible",
                "Un médicament que vous recherchiez est de nouveau disponible dans une pharmacie proche. Consultez vos alertes.",
                "/alertes");
        }
        log.info("Événement STOCK_DISPONIBLE {} : {} patient(s) prévenu(s)", event.id(), patients.size());
    }
}
