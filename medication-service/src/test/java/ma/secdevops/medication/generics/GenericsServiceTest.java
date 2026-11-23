package ma.secdevops.medication.generics;

import ma.secdevops.medication.generics.GenericsResponse.CheaperGeneric;
import ma.secdevops.medication.generics.GenericsResponse.Comparison;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static ma.secdevops.medication.generics.GenericsFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenericsServiceTest {

    private static GenericsService service(CatalogRow... rows) {
        return new GenericsService(GenericsFixtures.repository(rows));
    }

    private static final CatalogRow[] AUGMENTIN_FAMILY = {
            AUGMENTIN_16, ZAMOX_24, CURAM_16_NON_REMBOURSE, NEOCLAV_16, ACLAV_12_TYPO, ZAMOX_16_A, ZAMOX_16_B,
            CLAVULIN_PRINCEPS, MAXICLAV_AUTRE_DOSE, CLAVAM_PLUS_CHER, GENERIQUE_SANS_PRIX};

    @Test
    void cheaperGenericsAreSortedByUnitPrice() {
        GenericsResponse response = service(AUGMENTIN_FAMILY).cheaperGenerics(AUGMENTIN_16.code(), 10);

        assertThat(response.message()).isNull();
        assertThat(response.reference().prixUnitaire()).isEqualByComparingTo("10.51");
        // ZAMOX 24 sachets (6,96/sachet) passe devant les boîtes de 16 à 116 (7,25/sachet) puis ACLAV (8,25)
        assertThat(response.generiques()).extracting(CheaperGeneric::nom)
                .containsExactly("ZAMOX", "CURAM 1G/125MG", "NEOCLAV", "ZAMOX", "ACLAV");
        CheaperGeneric first = response.generiques().getFirst();
        assertThat(first.prixUnitaire()).isEqualByComparingTo("6.96");
        assertThat(first.economiePourcent()).isEqualTo(33);
        assertThat(first.comparaison()).isEqualTo(Comparison.PAR_UNITE);
    }

    @Test
    void excludesPrincepsOtherDoseMoreExpensiveAndUnpricedProducts() {
        GenericsResponse response = service(AUGMENTIN_FAMILY).cheaperGenerics(AUGMENTIN_16.code(), 10);

        assertThat(response.generiques()).extracting(CheaperGeneric::code).doesNotContain(
                AUGMENTIN_16.code(), CLAVULIN_PRINCEPS.code(), MAXICLAV_AUTRE_DOSE.code(),
                CLAVAM_PLUS_CHER.code(), GENERIQUE_SANS_PRIX.code());
    }

    @Test
    void sameProductUnderTwoCodesIsShownOnce() {
        GenericsResponse response = service(AUGMENTIN_16, ZAMOX_16_A, ZAMOX_16_B).cheaperGenerics(AUGMENTIN_16.code(), 10);

        assertThat(response.generiques()).hasSize(1);
    }

    @Test
    void sourceTypoInFormStillMatches() {
        GenericsResponse response = service(AUGMENTIN_16, ACLAV_12_TYPO).cheaperGenerics(AUGMENTIN_16.code(), 10);

        assertThat(response.generiques()).extracting(CheaperGeneric::nom).containsExactly("ACLAV");
    }

    @Test
    void flagsGenericThatIsNotReimbursed() {
        GenericsResponse response = service(AUGMENTIN_16, CURAM_16_NON_REMBOURSE, NEOCLAV_16)
                .cheaperGenerics(AUGMENTIN_16.code(), 10);

        assertThat(response.generiques()).filteredOn(g -> g.nom().startsWith("CURAM"))
                .singleElement().satisfies(g -> assertThat(g.moinsRembourse()).isTrue());
        assertThat(response.generiques()).filteredOn(g -> g.nom().equals("NEOCLAV"))
                .singleElement().satisfies(g -> assertThat(g.moinsRembourse()).isFalse());
    }

    @Test
    void oneGramEqualsThousandMilligramsButFormMustMatch() {
        GenericsResponse response = service(DOLIPRANE_EFF, ANDOL_EFF, PARACETAMOL_CP).cheaperGenerics(DOLIPRANE_EFF.code(), 10);

        // ANDOL (1000 MG, effervescent) oui ; comprimé simple non : ce n'est pas la même forme
        assertThat(response.generiques()).extracting(CheaperGeneric::nom).containsExactly("ANDOL");
        assertThat(response.generiques().getFirst().economiePourcent()).isEqualTo(8);
    }

    @Test
    void scoredTabletIsComparedWithPlainTablet() {
        GenericsResponse response = service(DOLIPRANE_SECABLE, PARACETAMOL_CP).cheaperGenerics(DOLIPRANE_SECABLE.code(), 10);

        assertThat(response.generiques()).extracting(CheaperGeneric::nom).containsExactly("PARA CP");
    }

    @Test
    void bottlesAreComparedOnlyWithTheSamePresentation() {
        GenericsResponse response = service(SIROP_REF, SIROP_MEME_FLACON, SIROP_AUTRE_FLACON)
                .cheaperGenerics(SIROP_REF.code(), 10);

        assertThat(response.reference().prixUnitaire()).isNull();
        assertThat(response.generiques()).singleElement().satisfies(g -> {
            assertThat(g.nom()).isEqualTo("SIROP GEN");
            assertThat(g.comparaison()).isEqualTo(Comparison.PAR_BOITE);
            assertThat(g.prixUnitaire()).isNull();
            assertThat(g.economiePourcent()).isEqualTo(20);
        });
    }

    @Test
    void emptyListComesWithAMessage() {
        GenericsResponse response = service(DOLIPRANE_SECABLE).cheaperGenerics(DOLIPRANE_SECABLE.code(), 10);

        assertThat(response.generiques()).isEmpty();
        assertThat(response.message()).isEqualTo(GenericsService.NONE_CHEAPER);
        assertThat(response.source()).contains("CNOPS");
    }

    @Test
    void referenceWithoutPriceDoseOrDciCannotBeCompared() {
        CatalogRow noPrice = new CatalogRow("6118000000301", "X", "PARACETAMOL", "1", null, "G", "COMPRIME",
                "1 BOITE 8 COMPRIME", null, 70, "P");
        CatalogRow noDose = new CatalogRow("6118000000302", "Y", "PARACETAMOL", null, null, "NA", "COMPRIME",
                "1 BOITE 8 COMPRIME", BigDecimal.TEN, 70, "P");
        CatalogRow noDci = new CatalogRow("6118000000303", "Z", null, "1", null, "G", "COMPRIME",
                "1 BOITE 8 COMPRIME", BigDecimal.TEN, 70, "P");
        GenericsService service = service(noPrice, noDose, noDci, PARACETAMOL_CP);

        assertThat(service.cheaperGenerics(noPrice.code(), 10).message()).isEqualTo(GenericsService.NO_PRICE);
        assertThat(service.cheaperGenerics(noDose.code(), 10).message()).isEqualTo(GenericsService.NO_DOSE);
        assertThat(service.cheaperGenerics(noDci.code(), 10).message()).isEqualTo(GenericsService.NO_DCI);
    }

    @Test
    void limitIsApplied() {
        GenericsResponse response = service(AUGMENTIN_FAMILY).cheaperGenerics(AUGMENTIN_16.code(), 2);

        assertThat(response.generiques()).hasSize(2);
    }

    @Test
    void unknownCodeIsNotFound() {
        assertThatThrownBy(() -> service(AUGMENTIN_16).cheaperGenerics("6118999999999", 10))
                .isInstanceOf(MedicationNotFoundException.class);
    }
}
