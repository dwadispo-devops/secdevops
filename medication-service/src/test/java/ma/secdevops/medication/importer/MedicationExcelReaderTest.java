package ma.secdevops.medication.importer;

import ma.secdevops.medication.model.Medication;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MedicationExcelReaderTest {
    @ParameterizedTest
    @CsvSource(value = {
            "200|200", "0,25|0.25", "600 / 5|NULL", "1.5 / 250|NULL",
            "10/10/5|NULL", "0.09 / 0.1 / 1 / 10|NULL", "NA / 0.25|NULL",
            "NA / 27|NULL", "35 000 / 35 000 / 100 000|NULL"
    }, delimiter = '|', nullValues = "NULL")
    void preservesSourceAndOnlyParsesScalar(String source, String numeric) throws Exception {
        Medication medication = read(source);
        assertThat(medication.dosageBrut()).isEqualTo(source);
        if (numeric == null) assertThat(medication.dosage1()).isNull();
        else assertThat(medication.dosage1()).isEqualByComparingTo(new BigDecimal(numeric));
    }

    @Test
    void preservesSurroundingWhitespaceAndComma() throws Exception {
        Medication medication = read("  0,25  ");
        assertThat(medication.dosageBrut()).isEqualTo("  0,25  ");
        assertThat(medication.dosage1()).isEqualByComparingTo("0.25");
    }

    @Test
    void emptyCellsHaveNoDosage() throws Exception {
        for (String source : new String[]{null, "", "   "}) {
            Medication medication = read(source);
            assertThat(medication.dosageBrut()).isNull();
            assertThat(medication.dosage1()).isNull();
        }
    }

    @Test
    void numericExcelCellRetainsExistingNumericBehavior() throws Exception {
        Medication medication = read(0.25);
        assertThat(medication.dosageBrut()).isEqualTo("0.25");
        assertThat(medication.dosage1()).isEqualByComparingTo("0.25");
    }

    private Medication read(Object source) throws Exception {
        byte[] bytes;
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("CODE");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("6118000000001");
            row.createCell(1).setCellValue("QA SYNTHETIQUE");
            if (source instanceof Number number) row.createCell(3).setCellValue(number.doubleValue());
            else if (source != null) row.createCell(3).setCellValue((String) source);
            workbook.write(output);
            bytes = output.toByteArray();
        }
        var result = new ImportResult();
        var medications = new MedicationExcelReader().read(new ByteArrayInputStream(bytes), result);
        assertThat(result.getIgnoredCount()).isZero();
        assertThat(medications).hasSize(1);
        return medications.getFirst();
    }
}
