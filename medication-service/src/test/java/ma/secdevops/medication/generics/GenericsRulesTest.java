package ma.secdevops.medication.generics;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GenericsRulesTest {

    private static CatalogRow dose(String brut, String unit) {
        return new CatalogRow("1", "X", "D", brut, null, unit, "COMPRIME", "1 BOITE 8 COMPRIME", BigDecimal.ONE, 70, "G");
    }

    @Test
    void dosesAreConvertedToMilligrams() {
        assertThat(GenericsRules.doseKey(dose("1", "G"))).isEqualTo(List.of("1000 MG"));
        assertThat(GenericsRules.doseKey(dose("1000", "MG"))).isEqualTo(List.of("1000 MG"));
        assertThat(GenericsRules.doseKey(dose("1 / 125", "G / MG"))).isEqualTo(List.of("1000 MG", "125 MG"));
        assertThat(GenericsRules.doseKey(dose("500 / 62.5", "MG / MG"))).isEqualTo(List.of("500 MG", "62.5 MG"));
        assertThat(GenericsRules.doseKey(dose("100", "MCG"))).isEqualTo(List.of("0.1 MG"));
        assertThat(GenericsRules.doseKey(dose("10", "UI"))).isEqualTo(List.of("10 UI"));
    }

    @Test
    void unreadableDoseCannotBeCompared() {
        assertThat(GenericsRules.doseKey(dose(null, "MG"))).isNull();
        assertThat(GenericsRules.doseKey(dose("1 / 125", "G"))).isNull();
        assertThat(GenericsRules.doseKey(dose("abc", "MG"))).isNull();
        // Repli sur le dosage numérique quand le texte source est absent
        CatalogRow numeric = new CatalogRow("1", "X", "D", null, new BigDecimal("500.000"), "MG", "GELULE",
                "1 BOITE 12 GELULE", BigDecimal.ONE, 70, "G");
        assertThat(GenericsRules.doseKey(numeric)).isEqualTo(List.of("500 MG"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "COMPRIME SECABLE;COMPRIME",
            "Comprimé sécable;COMPRIME",
            "POUDRE POUR SUSPENTION BUVABLE;POUDRE POUR SUSPENSION BUVABLE",
            "COMPRIME EFFERVESCENT;COMPRIME EFFERVESCENT",
            "GELULE GASTRO-RESISTANTE;GELULE GASTRO-RESISTANTE"
    })
    void formsAreNormalized(String source, String expected) {
        assertThat(GenericsRules.formKey(source)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', nullValues = "NULL", value = {
            "1 BOITE 16 SACHET;16",
            "1 BOITE 8 COMPRIME EFFERVESCENT;8",
            "1 BOITE 7 GELULE LP;7",
            "1 BOITE 1 FLACON 40 ML;NULL",
            "1 FLACON 60 ML;NULL",
            "1 TUBE 30 G;NULL",
            "1 BOITE 0 COMPRIME;NULL",
            ";NULL"
    })
    void countsUnitsInPresentation(String presentation, Integer expected) {
        assertThat(GenericsRules.unitCount(presentation)).isEqualTo(expected);
    }

    @Test
    void savingIsRoundedDown() {
        assertThat(GenericsRules.savingPercent(new BigDecimal("10"), new BigDecimal("6.69"))).isEqualTo(33);
        assertThat(GenericsRules.savingPercent(new BigDecimal("10"), new BigDecimal("9.99"))).isZero();
    }

    @Test
    void dciIgnoresCaseAccentsAndSpacing() {
        assertThat(GenericsRules.dciKey("Amoxicilline/acide clavulanique"))
                .isEqualTo(GenericsRules.dciKey("AMOXICILLINE / ACIDE CLAVULANIQUE"));
    }
}
