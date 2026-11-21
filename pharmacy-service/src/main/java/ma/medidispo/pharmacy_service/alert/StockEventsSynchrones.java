package ma.medidispo.pharmacy_service.alert;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import static ma.medidispo.pharmacy_service.alert.StockAlertDtos.StockAvailableEvent;

/** US22 : sans Kafka (tests, stock.kafka.enabled=false), les alertes sont traitées tout de suite. */
@Component
@ConditionalOnProperty(name = "stock.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class StockEventsSynchrones {

    private final StockAlertTrigger trigger;

    public StockEventsSynchrones(StockAlertTrigger trigger) { this.trigger = trigger; }

    @EventListener
    public void surEvenement(StockAvailableEvent event) { trigger.traiter(event); }
}
