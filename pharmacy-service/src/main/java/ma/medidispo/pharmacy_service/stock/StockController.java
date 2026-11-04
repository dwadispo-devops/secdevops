package ma.medidispo.pharmacy_service.stock;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/pharmacies/{pharmacyId}/stock")
public class StockController {

    private final StockService stockService;
    private final StockJwtService stockJwtService;

    public StockController(StockService stockService,
                           StockJwtService stockJwtService) {
        this.stockService = stockService;
        this.stockJwtService = stockJwtService;
    }

    public record UpdateStockRequest(StockStatus status) {}

    @PutMapping("/{medicationCode}")
    public ResponseEntity<Void> updateStock(
            @PathVariable UUID pharmacyId,
            @PathVariable String medicationCode,
            @RequestBody UpdateStockRequest request,
            @RequestHeader(value = "Authorization", required = false)
            String authorization) {

        UUID pharmacistId;

        try {
            pharmacistId = stockJwtService.authenticatedPharmacistId(authorization);
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acces refuse");
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token invalide");
        }

        if (medicationCode == null || !medicationCode.matches("[0-9]{1,13}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Code medicament invalide");
        }

        if (request == null || request.status() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Statut obligatoire");
        }

        stockService.updateStock(
                pharmacyId, medicationCode, request.status(), pharmacistId);

        return ResponseEntity.noContent().build();
    }
}