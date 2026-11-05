package ma.medidispo.pharmacy_service.stock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import ma.medidispo.pharmacy_service.pharmacy.Pharmacy;
import ma.medidispo.pharmacy_service.pharmacy.PharmacyRepository;

class StockServiceTest {

    @Test
    void shouldRejectPharmacistWhoDoesNotOwnPharmacy() {
        StockRepository stockRepository = mock(StockRepository.class);
        PharmacyRepository pharmacyRepository = mock(PharmacyRepository.class);
        Pharmacy pharmacy = mock(Pharmacy.class);

        UUID pharmacyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherPharmacistId = UUID.randomUUID();

        when(pharmacyRepository.findById(pharmacyId))
            .thenReturn(Optional.of(pharmacy));
        when(pharmacy.getOwnerUserId()).thenReturn(ownerId);

        StockService service = new StockService(
            stockRepository, pharmacyRepository);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.updateStock(
                pharmacyId, "123456", StockStatus.AVAILABLE,
                otherPharmacistId)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(stockRepository);
    }
    @Test
    void shouldAllowOwnerToUpdateStock() {
        StockRepository stockRepository = mock(StockRepository.class);
        PharmacyRepository pharmacyRepository = mock(PharmacyRepository.class);
        Pharmacy pharmacy = mock(Pharmacy.class);

        UUID pharmacyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        when(pharmacyRepository.findById(pharmacyId))
            .thenReturn(Optional.of(pharmacy));
        when(pharmacy.getOwnerUserId()).thenReturn(ownerId);

        StockService service = new StockService(
            stockRepository, pharmacyRepository);

        service.updateStock(
            pharmacyId, "123456", StockStatus.AVAILABLE, ownerId);

        verify(stockRepository).save(
            pharmacyId, "123456", StockStatus.AVAILABLE);
    }
}