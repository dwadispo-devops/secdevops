package ma.secdevops.medication.generics;

import ma.secdevops.medication.generics.GenericsResponse.CheaperGeneric;
import ma.secdevops.medication.generics.GenericsResponse.Comparison;
import ma.secdevops.medication.generics.GenericsResponse.Product;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * US16 – En tant que patient, je veux voir les génériques moins chers ayant la même DCI.
 *
 * <ol>
 *   <li>on lit le médicament de départ dans le catalogue ;</li>
 *   <li>on récupère les produits de même DCI ;</li>
 *   <li>on garde les génériques (G) de même dosage et même forme, avec un prix connu ;</li>
 *   <li>on compare le prix par unité (ou par boîte si le conditionnement est identique) ;</li>
 *   <li>on garde ceux qui sont strictement moins chers, du moins cher au plus cher.</li>
 * </ol>
 * Seuls des produits du catalogue officiel sont proposés (RG02) ; aucun conseil médical n'est donné.
 */
@Service
public class GenericsService {

    static final String NO_DCI = "La substance active (DCI) de ce médicament n'est pas renseignée : comparaison impossible.";
    static final String NO_PRICE = "Ce médicament n'a pas de prix public dans le catalogue : comparaison impossible.";
    static final String NO_DOSE = "Le dosage de ce médicament n'est pas renseigné : comparaison impossible.";
    static final String NONE_CHEAPER = "Aucun générique moins cher de même dosage et de même forme dans le catalogue.";

    private final GenericsRepository repository;

    public GenericsService(GenericsRepository repository) {
        this.repository = repository;
    }

    public GenericsResponse cheaperGenerics(String code, int limit) {
        CatalogRow ref = repository.findByCode(code).orElseThrow(MedicationNotFoundException::new);
        Integer refCount = GenericsRules.unitCount(ref.presentation());
        BigDecimal refUnitPrice = ref.ppv() != null && refCount != null
                ? GenericsRules.unitPrice(ref.ppv(), refCount) : null;
        Product reference = new Product(ref.code(), ref.nom(), ref.dci1(), ref.dosageLabel(), ref.forme(),
                ref.presentation(), ref.ppv(), money(refUnitPrice), ref.tauxRemboursement(), ref.princepsGenerique());

        if (ref.dci1() == null || ref.dci1().isBlank()) {
            return new GenericsResponse(reference, List.of(), NO_DCI, GenericsResponse.SOURCE);
        }
        if (ref.ppv() == null) {
            return new GenericsResponse(reference, List.of(), NO_PRICE, GenericsResponse.SOURCE);
        }
        List<String> refDose = GenericsRules.doseKey(ref);
        if (refDose == null) {
            return new GenericsResponse(reference, List.of(), NO_DOSE, GenericsResponse.SOURCE);
        }
        String refDci = GenericsRules.dciKey(ref.dci1());
        String refForm = GenericsRules.formKey(ref.forme());
        String refPresentation = GenericsRules.normalize(ref.presentation());

        List<Offer> offers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CatalogRow candidate : repository.findBySameDci(ref.dci1())) {
            if (candidate.code().equals(ref.code()) || !candidate.isGeneric() || candidate.ppv() == null
                    || !refDci.equals(GenericsRules.dciKey(candidate.dci1()))
                    || !refDose.equals(GenericsRules.doseKey(candidate))
                    || !refForm.equals(GenericsRules.formKey(candidate.forme()))) {
                continue;
            }
            Offer offer = compare(ref, refUnitPrice, refPresentation, candidate);
            // Le même produit est parfois présent sous deux codes (ex. ZAMOX) : on ne l'affiche qu'une fois
            String duplicateKey = GenericsRules.normalize(candidate.nom()) + "|"
                    + GenericsRules.normalize(candidate.presentation()) + "|" + candidate.ppv().stripTrailingZeros();
            if (offer != null && seen.add(duplicateKey)) {
                offers.add(offer);
            }
        }

        List<CheaperGeneric> generics = offers.stream()
                .sorted(Comparator.comparing(Offer::comparedPrice)
                        .thenComparing(o -> o.row().ppv())
                        .thenComparing(o -> o.row().nom()))
                .limit(limit)
                .map(o -> toDto(ref, o))
                .toList();
        return new GenericsResponse(reference, generics, generics.isEmpty() ? NONE_CHEAPER : null,
                GenericsResponse.SOURCE);
    }

    /** @return l'offre si le générique est strictement moins cher, sinon null (ou si non comparable). */
    private static Offer compare(CatalogRow ref, BigDecimal refUnitPrice, String refPresentation, CatalogRow candidate) {
        Integer count = GenericsRules.unitCount(candidate.presentation());
        if (refUnitPrice != null && count != null) {
            BigDecimal unitPrice = GenericsRules.unitPrice(candidate.ppv(), count);
            return unitPrice.compareTo(refUnitPrice) < 0
                    ? new Offer(candidate, unitPrice, unitPrice, refUnitPrice, Comparison.PAR_UNITE) : null;
        }
        // Flacon, tube... : comparable seulement si le conditionnement est identique
        if (refPresentation.equals(GenericsRules.normalize(candidate.presentation()))) {
            return candidate.ppv().compareTo(ref.ppv()) < 0
                    ? new Offer(candidate, null, candidate.ppv(), ref.ppv(), Comparison.PAR_BOITE) : null;
        }
        return null;
    }

    private static CheaperGeneric toDto(CatalogRow ref, Offer o) {
        CatalogRow g = o.row();
        boolean lessReimbursed = ref.tauxRemboursement() != null && ref.tauxRemboursement() > 0
                && (g.tauxRemboursement() == null || g.tauxRemboursement() < ref.tauxRemboursement());
        return new CheaperGeneric(g.code(), g.nom(), g.dosageLabel(), g.forme(), g.presentation(), g.ppv(),
                money(o.unitPrice()), GenericsRules.savingPercent(o.referencePrice(), o.comparedPrice()),
                o.comparison(), g.tauxRemboursement(), lessReimbursed);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private record Offer(CatalogRow row, BigDecimal unitPrice, BigDecimal comparedPrice,
                         BigDecimal referencePrice, Comparison comparison) {
        Offer {
            Objects.requireNonNull(comparedPrice);
        }
    }
}
