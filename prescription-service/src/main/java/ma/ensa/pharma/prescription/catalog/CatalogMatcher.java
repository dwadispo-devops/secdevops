package ma.ensa.pharma.prescription.catalog;

import ma.ensa.pharma.prescription.api.dto.LigneOrdonnanceDto;
import ma.ensa.pharma.prescription.api.dto.ProduitDto;
import ma.ensa.pharma.prescription.config.CatalogProperties;
import ma.ensa.pharma.prescription.domain.ReadLine;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rapprochement des lignes lues avec le catalogue CNOPS (US07, étape « Retrieval » du RAG) :
 * <ol>
 *   <li>recherche des candidats dans medication-service à partir du nom lu ;</li>
 *   <li>score de confiance de chaque candidat ({@link MatchScorer}) ;</li>
 *   <li>conservation des 3 meilleurs, du plus proche au moins proche.</li>
 * </ol>
 * RG02 : seuls des produits renvoyés par le catalogue sont proposés, jamais un nom inventé par l'IA.
 */
@Component
public class CatalogMatcher {

    static final int MAX_SUGGESTIONS = 3;
    /** Plafond « à vérifier » : juste sous le seuil vert de 0,85. */
    static final double CAP_TO_VERIFY = 0.84;
    /** Écart de score sous lequel deux produits différents sont considérés comme ex æquo. */
    static final double TIE_MARGIN = 0.02;

    private final CatalogClient catalogClient;
    private final MatchScorer scorer;
    private final CatalogProperties properties;

    public CatalogMatcher(CatalogClient catalogClient, MatchScorer scorer, CatalogProperties properties) {
        this.catalogClient = catalogClient;
        this.scorer = scorer;
        this.properties = properties;
    }

    public MatchResult match(List<ReadLine> lines) {
        List<LigneOrdonnanceDto> result = new ArrayList<>();
        boolean catalogAvailable = true;
        for (ReadLine line : lines) {
            List<Scored> ranked = List.of();
            if (!line.illegible() && catalogAvailable) {
                try {
                    ranked = rank(line, findCandidates(line.name()));
                } catch (CatalogUnavailableException e) {
                    // On n'insiste pas pour les lignes suivantes : le patient vérifiera à la main
                    catalogAvailable = false;
                }
            }
            result.add(toDto(line, ranked));
        }
        return new MatchResult(result, !catalogAvailable);
    }

    /** Recherche par le nom complet, puis par le premier mot si rien n'est trouvé. */
    private List<CatalogMedication> findCandidates(String name) {
        String query = truncate(name.strip());
        if (query.length() < 2) {
            return List.of();
        }
        List<CatalogMedication> found = catalogClient.search(query, properties.candidates());
        if (found.isEmpty() && query.contains(" ")) {
            String firstWord = query.substring(0, query.indexOf(' '));
            if (firstWord.length() >= 2) {
                found = catalogClient.search(firstWord, properties.candidates());
            }
        }
        return found;
    }

    private List<Scored> rank(ReadLine line, List<CatalogMedication> candidates) {
        // Un même produit (même code) ne doit apparaître qu'une fois
        Map<String, CatalogMedication> unique = new LinkedHashMap<>();
        for (CatalogMedication candidate : candidates) {
            if (candidate != null && candidate.code() != null) {
                unique.putIfAbsent(candidate.code(), candidate);
            }
        }
        return unique.values().stream()
                .map(candidate -> new Scored(candidate, scorer.score(line, candidate)))
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .toList();
    }

    private static LigneOrdonnanceDto toDto(ReadLine line, List<Scored> ranked) {
        List<ProduitDto> suggestions = ranked.stream()
                .limit(MAX_SUGGESTIONS)
                .map(s -> new ProduitDto(s.medication().code(), s.medication().label()))
                .toList();
        double score = ranked.isEmpty() ? 0.0 : ranked.get(0).score();
        if (isAmbiguous(ranked) || line.uncertain()) {
            // Deux produits différents aussi probables, ou doute de l'IA : le patient doit choisir
            score = Math.min(score, CAP_TO_VERIFY);
        }
        return new LigneOrdonnanceDto(line.id(), line.readText(), line.quantity(), score, suggestions);
    }

    /**
     * Ambigu si les deux meilleurs candidats ont presque le même score mais ne désignent pas
     * le même médicament (nom, dosage ou forme différents). Deux présentations d'un même
     * médicament (boîte de 8 ou de 16) ne sont pas considérées comme ambiguës.
     */
    static boolean isAmbiguous(List<Scored> ranked) {
        if (ranked.size() < 2) {
            return false;
        }
        Scored first = ranked.get(0);
        Scored second = ranked.get(1);
        return first.score() - second.score() < TIE_MARGIN
                && !sameMedicine(first.medication(), second.medication());
    }

    private static boolean sameMedicine(CatalogMedication a, CatalogMedication b) {
        return MatchScorer.normalize(a.nom()).equals(MatchScorer.normalize(b.nom()))
                && sameDose(a, b)
                && MatchScorer.normalize(a.forme()).equals(MatchScorer.normalize(b.forme()));
    }

    private static boolean sameDose(CatalogMedication a, CatalogMedication b) {
        if (a.dosage1() == null || b.dosage1() == null) {
            return a.dosage1() == null && b.dosage1() == null;
        }
        return MatchScorer.Dose.of(a.dosage1().doubleValue(), a.uniteDosage1())
                .sameAs(MatchScorer.Dose.of(b.dosage1().doubleValue(), b.uniteDosage1()));
    }

    private static String truncate(String query) {
        return query.length() > 50 ? query.substring(0, 50).strip() : query;
    }

    record Scored(CatalogMedication medication, double score) {
    }

    /**
     * @param lignes             lignes rapprochées du catalogue
     * @param catalogUnavailable true si le catalogue n'a pas répondu (suggestions vides)
     */
    public record MatchResult(List<LigneOrdonnanceDto> lignes, boolean catalogUnavailable) {
    }
}
