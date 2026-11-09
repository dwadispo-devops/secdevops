package ma.ensa.pharma.prescription.service;

import ma.ensa.pharma.prescription.api.dto.PrescriptionEstimateDtos.*;
import ma.ensa.pharma.prescription.catalog.FinancialCatalogClient;
import ma.ensa.pharma.prescription.exception.PrescriptionException;
import ma.ensa.pharma.prescription.storage.PrescriptionRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PrescriptionEstimateService {
    public static final String REIMBURSEMENT_EXPLANATION =
            "Remboursement non calculable : la base de remboursement n'est pas confirmée dans les données disponibles. "
            + "Le taux du catalogue seul ne suffit pas. Le reste à charge est également non calculable.";
    private final FinancialCatalogClient catalog;
    private final PrescriptionRepository prescriptions;
    public PrescriptionEstimateService(FinancialCatalogClient catalog, PrescriptionRepository prescriptions) {
        this.catalog = catalog; this.prescriptions = prescriptions;
    }

    public Estimate estimate(UUID patient, Request request) {
        if (request.prescriptionId() != null) {
            prescriptions.findById(request.prescriptionId()).filter(p -> p.belongsTo(patient))
                    .orElseThrow(PrescriptionException::notFound);
        }
        var codes = request.items().stream().map(Item::code).distinct().toList();
        var medications = catalog.lookup(codes).stream().filter(Objects::nonNull)
                .collect(Collectors.toMap(FinancialCatalogClient.Medication::code, Function.identity(), (a, b) -> a));
        var lines = request.items().stream().map(item -> line(item, medications.get(item.code()))).toList();
        var subtotal = lines.stream().map(Line::estimatedCost).filter(Objects::nonNull)
                .reduce(new BigDecimal("0.00"), BigDecimal::add);
        boolean complete = lines.stream().allMatch(l -> l.estimatedCost() != null);
        return new Estimate("MAD", "Référentiel CNOPS 2014 — simulation indicative, prix non garantis à jour",
                Instant.now(), lines, complete, subtotal, complete ? subtotal : null, null, null,
                REIMBURSEMENT_EXPLANATION);
    }

    private Line line(Item item, FinancialCatalogClient.Medication medication) {
        String reason = null;
        if (medication == null) reason = "Code absent du catalogue.";
        else if (medication.ppv() == null) reason = "Prix public indisponible ; aucun prix de remplacement n'est appliqué.";
        else if (medication.ppv().signum() < 0 || medication.ppv().scale() > 2)
            reason = "Prix public incohérent dans le catalogue.";
        else if (medication.presentation() == null || medication.presentation().isBlank())
            reason = "Conditionnement absent : l'unité du prix ne peut pas être vérifiée.";
        else if (!item.unitConfirmed()) reason = "Confirmez que la quantité correspond au conditionnement du catalogue.";
        BigDecimal cost = reason == null ? medication.ppv().multiply(BigDecimal.valueOf(item.quantity())).setScale(2) : null;
        return new Line(item.code(), medication == null ? null : medication.nom(),
                medication == null ? null : medication.presentation(), item.quantity(),
                medication == null ? null : medication.ppv(), medication == null ? null : medication.tauxRemboursement(), cost, reason);
    }
}
