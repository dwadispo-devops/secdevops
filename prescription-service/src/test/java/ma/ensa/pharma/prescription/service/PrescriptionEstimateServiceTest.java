package ma.ensa.pharma.prescription.service;

import ma.ensa.pharma.prescription.api.dto.PrescriptionEstimateDtos.*;
import ma.ensa.pharma.prescription.catalog.FinancialCatalogClient;
import ma.ensa.pharma.prescription.catalog.FinancialCatalogClient.Medication;
import ma.ensa.pharma.prescription.exception.PrescriptionException;
import ma.ensa.pharma.prescription.storage.*;
import ma.ensa.pharma.prescription.domain.DetectedFileType;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrescriptionEstimateServiceTest {
    final FinancialCatalogClient catalog = mock(FinancialCatalogClient.class);
    final PrescriptionRepository repository = mock(PrescriptionRepository.class);
    final PrescriptionEstimateService service = new PrescriptionEstimateService(catalog, repository);
    final UUID patient = UUID.randomUUID();
    final String code = "6118000000001";
    Medication med(String price, String presentation, Integer rate) {
        return new Medication(code, "Produit de test", presentation, price == null ? null : new BigDecimal(price), rate);
    }
    Estimate calculate(Medication medication, boolean confirmed) {
        when(catalog.lookup(List.of(code))).thenReturn(List.of(medication));
        return service.estimate(patient, new Request(null, List.of(new Item(code, 3, confirmed))));
    }
    @Test void exactCostAndNoInventedReimbursementEvenWithSeventyPercent() {
        var estimate = calculate(med("12.85", "1 BOITE 12 SACHET", 70), true);
        assertThat(estimate.totalEstimatedCost()).isEqualByComparingTo("38.55");
        assertThat(estimate.complete()).isTrue();
        assertThat(estimate.lines().getFirst().catalogRate()).isEqualTo(70);
        assertThat(estimate.reimbursableAmount()).isNull();
        assertThat(estimate.remainingCharge()).isNull();
        assertThat(estimate.reimbursementExplanation()).contains("base de remboursement n'est pas confirmée");
        verifyNoInteractions(repository);
    }
    @Test void zeroAndMissingRatesAreNotConfusedOrUsedAsRules() {
        for (Integer rate : Arrays.asList(0, null)) {
            var estimate = calculate(med("0.10", "1 FLACON", rate), true);
            assertThat(estimate.totalEstimatedCost()).isEqualByComparingTo("0.30");
            assertThat(estimate.lines().getFirst().catalogRate()).isEqualTo(rate);
            assertThat(estimate.reimbursableAmount()).isNull();
        }
    }
    @Test void missingPriceUnitAndInvalidPricePreventCost() {
        for (var medication : List.of(med(null, "1 BOITE", 70), med("10.00", null, 70),
                med("-1.00", "1 BOITE", 70), med("1.001", "1 BOITE", 70))) {
            var estimate = calculate(medication, true);
            assertThat(estimate.complete()).isFalse();
            assertThat(estimate.totalEstimatedCost()).isNull();
            assertThat(estimate.lines().getFirst().reason()).isNotBlank();
        }
        assertThat(calculate(med("10.00", "1 BOITE", 70), false).totalEstimatedCost()).isNull();
    }
    @Test void unknownCodeAndPartialSubtotalAndDuplicates() {
        when(catalog.lookup(List.of(code, "6118000000002"))).thenReturn(List.of(med("12.85", "1 BOITE", 70)));
        var estimate = service.estimate(patient, new Request(null, List.of(
                new Item(code, 2, true), new Item("6118000000002", 1, true), new Item(code, 1, true))));
        assertThat(estimate.lines()).hasSize(3);
        assertThat(estimate.complete()).isFalse();
        assertThat(estimate.knownSubtotal()).isEqualByComparingTo("38.55");
        assertThat(estimate.totalEstimatedCost()).isNull();
        assertThat(estimate.lines().get(1).reason()).contains("absent du catalogue");
        verify(catalog).lookup(List.of(code, "6118000000002"));
    }
    @Test void ownershipCheckedBeforeCallingCatalog() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(new Prescription(UUID.randomUUID(), DetectedFileType.PNG, 1)));
        assertThatThrownBy(() -> service.estimate(patient, new Request(id, List.of(new Item(code, 1, true)))))
                .isInstanceOf(PrescriptionException.class);
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.estimate(patient, new Request(id, List.of(new Item(code, 1, true)))))
                .isInstanceOf(PrescriptionException.class);
        verifyNoInteractions(catalog);
    }
    @Test void ownPrescriptionIsNotWritten() {
        var p = new Prescription(patient, DetectedFileType.PNG, 1);
        when(repository.findById(p.getId())).thenReturn(Optional.of(p));
        when(catalog.lookup(List.of(code))).thenReturn(List.of(med("1.00", "1 BOITE", 70)));
        assertThat(service.estimate(patient, new Request(p.getId(), List.of(new Item(code, 10000, true))))
                .totalEstimatedCost()).isEqualByComparingTo("10000.00");
        verify(repository).findById(p.getId());
        verifyNoMoreInteractions(repository);
    }
}
