package ma.ensa.pharma.prescription.catalog;

import ma.ensa.pharma.prescription.api.dto.LigneOrdonnanceDto;
import ma.ensa.pharma.prescription.api.dto.ProduitDto;
import ma.ensa.pharma.prescription.config.CatalogProperties;
import ma.ensa.pharma.prescription.domain.ReadLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static ma.ensa.pharma.prescription.catalog.CatalogFixtures.AUGMENTIN_1G_SACHET;
import static ma.ensa.pharma.prescription.catalog.CatalogFixtures.AUGMENTIN_500_SACHET;
import static ma.ensa.pharma.prescription.catalog.CatalogFixtures.AUGMENTIN_CP_NO_DOSE;
import static ma.ensa.pharma.prescription.catalog.CatalogFixtures.AUGMENTIN_SACHET_NO_DOSE;
import static ma.ensa.pharma.prescription.catalog.CatalogFixtures.AUGMENTIN_SACHET_NO_DOSE_24;
import static ma.ensa.pharma.prescription.catalog.CatalogFixtures.DOLIPRANE_1000_CP;
import static ma.ensa.pharma.prescription.catalog.CatalogFixtures.DOLIPRANE_1000_CP_16;
import static ma.ensa.pharma.prescription.catalog.CatalogFixtures.DOLIPRANE_1000_SACHET;
import static ma.ensa.pharma.prescription.catalog.CatalogFixtures.DOLIPRANE_500_CP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogMatcherTest {

    private CatalogClient catalog;
    private CatalogMatcher matcher;

    @BeforeEach
    void setUp() {
        catalog = mock(CatalogClient.class);
        matcher = new CatalogMatcher(catalog, new MatchScorer(),
                new CatalogProperties("http://localhost:8083", Duration.ofSeconds(3), 10));
    }

    private static ReadLine line(int id, String name, String dosage, String form, Integer qty) {
        return new ReadLine(id, name, dosage, form, qty, false);
    }

    @Test
    void exactMatchIsGreenWithBestSuggestionFirst() {
        when(catalog.search(eq("DOLIPRANE"), anyInt())).thenReturn(List.of(
                DOLIPRANE_500_CP, DOLIPRANE_1000_SACHET, DOLIPRANE_1000_CP));

        LigneOrdonnanceDto ligne = matcher.match(List.of(line(1, "DOLIPRANE", "1 g", "comprimé", 2)))
                .lignes().get(0);

        assertThat(ligne.id()).isEqualTo(1);
        assertThat(ligne.texteLu()).isEqualTo("DOLIPRANE 1 g");
        assertThat(ligne.quantite()).isEqualTo(2);
        assertThat(ligne.score()).isGreaterThanOrEqualTo(0.85);
        assertThat(ligne.suggestions()).hasSize(3);
        assertThat(ligne.suggestions().get(0))
                .isEqualTo(new ProduitDto(DOLIPRANE_1000_CP.code(), DOLIPRANE_1000_CP.label()));
    }

    @Test
    void keepsAtMostThreeSuggestionsWithoutDuplicates() {
        when(catalog.search(anyString(), anyInt())).thenReturn(List.of(
                DOLIPRANE_1000_CP, DOLIPRANE_1000_CP, DOLIPRANE_1000_CP_16, DOLIPRANE_500_CP,
                DOLIPRANE_1000_SACHET, AUGMENTIN_1G_SACHET));

        LigneOrdonnanceDto ligne = matcher.match(List.of(line(1, "DOLIPRANE", "1 g", "comprimé", 1)))
                .lignes().get(0);

        assertThat(ligne.suggestions()).hasSize(3);
        assertThat(ligne.suggestions()).extracting(ProduitDto::id).doesNotHaveDuplicates();
    }

    @Test
    void twoBoxSizesOfTheSameMedicineAreNotAmbiguous() {
        when(catalog.search(anyString(), anyInt())).thenReturn(List.of(DOLIPRANE_1000_CP, DOLIPRANE_1000_CP_16));

        LigneOrdonnanceDto ligne = matcher.match(List.of(line(1, "DOLIPRANE", "1 g", "comprimé", 1)))
                .lignes().get(0);

        assertThat(ligne.score()).isGreaterThanOrEqualTo(0.85);
    }

    @Test
    void missingDosageWithSeveralDosagesInCatalogIsOrange() {
        when(catalog.search(anyString(), anyInt())).thenReturn(List.of(DOLIPRANE_1000_CP, DOLIPRANE_500_CP));

        LigneOrdonnanceDto ligne = matcher.match(List.of(line(1, "DOLIPRANE", "", "comprimé", 1)))
                .lignes().get(0);

        assertThat(ligne.score()).isBetween(0.60, 0.84);
        assertThat(ligne.suggestions()).hasSize(2);
    }

    @Test
    void uncertainLineIsNeverGreen() {
        when(catalog.search(anyString(), anyInt())).thenReturn(List.of(DOLIPRANE_1000_CP));

        LigneOrdonnanceDto ligne = matcher.match(List.of(
                new ReadLine(1, "DOLIPRANE", "1 g", "comprimé", 1, true))).lignes().get(0);

        assertThat(ligne.score()).isEqualTo(CatalogMatcher.CAP_TO_VERIFY);
    }

    @Test
    void wrongDosageInCatalogIsOrange() {
        when(catalog.search(anyString(), anyInt())).thenReturn(List.of(AUGMENTIN_500_SACHET));

        LigneOrdonnanceDto ligne = matcher.match(List.of(line(1, "AUGMENTIN", "1 g", "sachet", 1)))
                .lignes().get(0);

        assertThat(ligne.score()).isBetween(0.60, 0.84);
    }

    @Test
    void realCnopsAugmentinCaseProposesTheSachetFirst() {
        // Ordre renvoyé par le vrai catalogue lors du test : le comprimé arrive en premier
        when(catalog.search(anyString(), anyInt())).thenReturn(List.of(
                AUGMENTIN_CP_NO_DOSE, AUGMENTIN_SACHET_NO_DOSE, AUGMENTIN_SACHET_NO_DOSE_24));

        LigneOrdonnanceDto ligne = matcher.match(List.of(
                line(1, "AUGMENTIN", "1 g/125 mg", "poudre en sachet", 2))).lignes().get(0);

        assertThat(ligne.suggestions().get(0).id()).isEqualTo(AUGMENTIN_SACHET_NO_DOSE.code());
        assertThat(ligne.suggestions().get(2).id()).isEqualTo(AUGMENTIN_CP_NO_DOSE.code());
        assertThat(ligne.score()).isBetween(0.60, 0.84);
    }

    @Test
    void nothingFoundIsRedWithoutSuggestion() {
        when(catalog.search(anyString(), anyInt())).thenReturn(List.of());

        LigneOrdonnanceDto ligne = matcher.match(List.of(line(1, "SUPERCURE", "500 mg", "", 1)))
                .lignes().get(0);

        assertThat(ligne.score()).isZero();
        assertThat(ligne.suggestions()).isEmpty();
    }

    @Test
    void retriesWithFirstWordWhenFullNameFindsNothing() {
        when(catalog.search(eq("DOLIPRANE ADULTE"), anyInt())).thenReturn(List.of());
        when(catalog.search(eq("DOLIPRANE"), anyInt())).thenReturn(List.of(DOLIPRANE_1000_CP));

        LigneOrdonnanceDto ligne = matcher.match(List.of(line(1, "DOLIPRANE ADULTE", "1 g", "cp", 1)))
                .lignes().get(0);

        assertThat(ligne.suggestions()).extracting(ProduitDto::id).containsExactly(DOLIPRANE_1000_CP.code());
    }

    @Test
    void illegibleLineIsRedAndNeverSentToCatalog() {
        LigneOrdonnanceDto ligne = matcher.match(List.of(line(1, "", "", "", null))).lignes().get(0);

        assertThat(ligne.texteLu()).isEqualTo("(illisible)");
        assertThat(ligne.score()).isZero();
        assertThat(ligne.suggestions()).isEmpty();
        verify(catalog, never()).search(anyString(), anyInt());
    }

    @Test
    void catalogDownGivesRedLinesAndStopsCalling() {
        when(catalog.search(anyString(), anyInt()))
                .thenThrow(new CatalogUnavailableException("down", new RuntimeException()));

        CatalogMatcher.MatchResult result = matcher.match(List.of(
                line(1, "DOLIPRANE", "1 g", "cp", 1),
                line(2, "AUGMENTIN", "1 g", "sachet", 1)));

        assertThat(result.catalogUnavailable()).isTrue();
        assertThat(result.lignes()).hasSize(2).allMatch(l -> l.score() == 0 && l.suggestions().isEmpty());
        verify(catalog, times(1)).search(anyString(), anyInt());
    }

    @Test
    void neverProposesAProductThatIsNotInTheCatalog() {
        when(catalog.search(anyString(), anyInt())).thenReturn(List.of(AUGMENTIN_1G_SACHET));

        LigneOrdonnanceDto ligne = matcher.match(List.of(line(1, "SUPERCURE", "500 mg", "", 1)))
                .lignes().get(0);

        // Le seul produit proposé est celui du catalogue, avec un score rouge
        assertThat(ligne.suggestions()).extracting(ProduitDto::id).containsExactly(AUGMENTIN_1G_SACHET.code());
        assertThat(ligne.score()).isLessThan(0.60);
    }
}
