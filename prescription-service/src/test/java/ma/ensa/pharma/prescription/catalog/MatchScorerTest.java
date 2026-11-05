package ma.ensa.pharma.prescription.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static ma.ensa.pharma.prescription.catalog.CatalogFixtures.AUGMENTIN_1G_SACHET;
import static ma.ensa.pharma.prescription.catalog.CatalogFixtures.AUGMENTIN_CP_NO_DOSE;
import static ma.ensa.pharma.prescription.catalog.CatalogFixtures.AUGMENTIN_SACHET_NO_DOSE;
import static ma.ensa.pharma.prescription.catalog.CatalogFixtures.DOLIPRANE_1000_CP;
import static ma.ensa.pharma.prescription.catalog.CatalogFixtures.DOLIPRANE_1000_SACHET;
import static ma.ensa.pharma.prescription.catalog.CatalogFixtures.DOLIPRANE_500_CP;
import static ma.ensa.pharma.prescription.catalog.CatalogFixtures.line;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MatchScorerTest {

    private final MatchScorer scorer = new MatchScorer();

    // ------------------------------------------------ scénarios de bout en bout (seuils §5.1)

    @Test
    void exactMatchIsGreen() {
        double score = scorer.score(line("DOLIPRANE", "1 g", "comprimé"), DOLIPRANE_1000_CP);
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void abbreviatedFormAndGramsStillGreen() {
        assertThat(scorer.score(line("Doliprane", "1g", "cp"), DOLIPRANE_1000_CP)).isGreaterThanOrEqualTo(0.85);
        assertThat(scorer.score(line("AUGMENTIN", "1 g/125 mg", "sachet"), AUGMENTIN_1G_SACHET)).isEqualTo(1.0);
    }

    @Test
    void wrongDosageIsOrange() {
        double score = scorer.score(line("DOLIPRANE", "1 g", "comprimé"), DOLIPRANE_500_CP);
        assertThat(score).isBetween(0.60, 0.84);
    }

    @Test
    void wrongFormIsOrange() {
        double score = scorer.score(line("DOLIPRANE", "1 g", "comprimé"), DOLIPRANE_1000_SACHET);
        assertThat(score).isBetween(0.60, 0.84);
    }

    @Test
    void differentMedicineIsRed() {
        double score = scorer.score(line("SPASFON", "80 mg", "comprimé"), AUGMENTIN_1G_SACHET);
        assertThat(score).isLessThan(0.60);
    }

    @Test
    void misreadNameStaysCloseToTheRightProduct() {
        double misread = scorer.score(line("DOLIPRAN", "1 g", "comprimé"), DOLIPRANE_1000_CP);
        double wrong = scorer.score(line("DOLIPRAN", "1 g", "comprimé"), AUGMENTIN_1G_SACHET);
        assertThat(misread).isGreaterThan(0.80).isGreaterThan(wrong);
    }

    @Test
    void sachetFoundInPresentationRanksAboveTablet() {
        // Cas réel CNOPS : forme "POUDRE POUR SUSPENSION BUVABLE", "SACHET" seulement dans la présentation
        double sachet = scorer.score(line("AUGMENTIN", "1 g/125 mg", "poudre en sachet"), AUGMENTIN_SACHET_NO_DOSE);
        double tablet = scorer.score(line("AUGMENTIN", "1 g/125 mg", "poudre en sachet"), AUGMENTIN_CP_NO_DOSE);
        assertThat(sachet).isGreaterThan(tablet);
    }

    @Test
    void unknownCatalogDosageCannotBeGreen() {
        // Le dosage lu ne peut pas être vérifié : le patient doit confirmer
        double score = scorer.score(line("AUGMENTIN", "1 g/125 mg", "sachet"), AUGMENTIN_SACHET_NO_DOSE);
        assertThat(score).isBetween(0.60, 0.84);
    }

    @Test
    void usesRawDosageWhenStructuredDosageIsMissing() {
        CatalogMedication withRaw = new CatalogMedication("1", "AUGMENTIN", null, null, null,
                "POUDRE POUR SUSPENSION BUVABLE", "1 BOITE 12 SACHET", "1 G/125 MG");
        assertThat(scorer.score(line("AUGMENTIN", "1 g/125 mg", "sachet"), withRaw)).isEqualTo(1.0);
        assertThat(withRaw.label()).isEqualTo("AUGMENTIN 1 G/125 MG, POUDRE POUR SUSPENSION BUVABLE, 1 BOITE 12 SACHET");
    }

    @Test
    void rawDosageWithoutUnitIsIgnored() {
        CatalogMedication rawNoUnit = new CatalogMedication("1", "DOLIPRANE", null, null, null,
                "COMPRIME", "1 BOITE 10 COMPRIME", "1");
        assertThat(MatchScorer.catalogDose(rawNoUnit)).isNull();
    }

    // ------------------------------------------------ nom

    @Test
    void identicalNamesHaveSimilarityOne() {
        assertThat(MatchScorer.nameSimilarity("Doliprane", "DOLIPRANE")).isEqualTo(1.0);
    }

    @Test
    void accentsAndCaseAreIgnored() {
        assertThat(MatchScorer.normalize("Éfféralgan  Pédiatrique")).isEqualTo("efferalgan pediatrique");
    }

    @Test
    void emptyNameHasZeroSimilarity() {
        assertThat(MatchScorer.nameSimilarity("", "DOLIPRANE")).isZero();
        assertThat(MatchScorer.nameSimilarity(null, "DOLIPRANE")).isZero();
    }

    // ------------------------------------------------ dosage

    @ParameterizedTest
    @CsvSource({
            "1 g, 1000, MG, 1.0",
            "1000mg, 1, G, 1.0",
            "500 mg, 1000, MG, 0.0",
            "'1 g/125 mg', 1, G, 1.0",
            "'0,5 g', 500, MG, 1.0",
            "100 µg, 0.1, MG, 1.0",
            "10 UI, 10, UI, 1.0",
            "10 UI, 10, MG, 0.0"
    })
    void comparesDosagesAfterConversion(String read, String value, String unit, double expected) {
        assertThat(MatchScorer.dosageScore(read, new BigDecimal(value), unit)).isEqualTo(expected);
    }

    @Test
    void unknownDosageIsNeutral() {
        assertThat(MatchScorer.dosageScore("", new BigDecimal("1000"), "MG")).isEqualTo(MatchScorer.UNKNOWN_DOSAGE);
        assertThat(MatchScorer.dosageScore("1 g", null, null)).isEqualTo(MatchScorer.UNKNOWN_DOSAGE);
        assertThat(MatchScorer.dosageScore("sans dosage", new BigDecimal("1"), "G")).isEqualTo(MatchScorer.UNKNOWN_DOSAGE);
    }

    @Test
    void parsesFirstDose() {
        MatchScorer.Dose dose = MatchScorer.parseDose("1 g/125 mg");
        assertThat(dose.kind()).isEqualTo("mg");
        assertThat(dose.value()).isCloseTo(1000, within(0.001));
    }

    // ------------------------------------------------ forme

    @ParameterizedTest
    @CsvSource({
            "cp, COMPRIME",
            "comprimés, COMPRIME PELLICULE",
            "sachet, POUDRE EN SACHET",
            "poudre en sachet, POUDRE EN SACHET",
            "sirop, SIROP",
            "gélule, GELULE",
            "suppo, SUPPOSITOIRE"
    })
    void recognisesSameForm(String read, String catalog) {
        assertThat(MatchScorer.formScore(read, catalog)).isEqualTo(1.0);
    }

    @Test
    void formCanBeFoundInPresentation() {
        assertThat(MatchScorer.formScore("sachet", "POUDRE POUR SUSPENSION BUVABLE", "1 BOITE 16 SACHET"))
                .isEqualTo(1.0);
        assertThat(MatchScorer.formScore("comprimé", "POUDRE POUR SUSPENSION BUVABLE", "1 BOITE 16 SACHET"))
                .isZero();
    }

    @Test
    void repeatedWordsInPresentationDoNotCrash() {
        // Bug corrigé : "1 BOITE 1 FLACON" contient deux fois "1" (Set.of refusait les doublons → erreur 500)
        assertThat(MatchScorer.formScore("sirop", "SIROP", "1 BOITE 1 FLACON 1 FLACON")).isEqualTo(1.0);
        assertThat(MatchScorer.canonicalForms("COMPRIME, 1 BOITE 1 PLAQUETTE 10 COMPRIME")).containsExactly("comprime");
    }

    @Test
    void differentFormsDoNotMatch() {
        assertThat(MatchScorer.formScore("comprimé", "POUDRE EN SACHET")).isZero();
        assertThat(MatchScorer.formScore("sachet", "POUDRE POUR SOLUTION BUVABLE")).isZero();
    }

    @Test
    void unknownFormIsNeutral() {
        assertThat(MatchScorer.formScore("", "COMPRIME")).isEqualTo(MatchScorer.UNKNOWN_FORM);
        assertThat(MatchScorer.formScore("truc", "COMPRIME")).isEqualTo(MatchScorer.UNKNOWN_FORM);
    }

    // ------------------------------------------------ libellé

    @Test
    void buildsReadableLabel() {
        assertThat(DOLIPRANE_1000_CP.label()).isEqualTo("DOLIPRANE 1000 MG, COMPRIME, 1 BOITE 8 COMPRIME");
        assertThat(AUGMENTIN_1G_SACHET.label()).isEqualTo("AUGMENTIN 1 G, POUDRE EN SACHET, 1 BOITE 12 SACHET");
    }
}
