package ma.medidispo.pharmacy_service.reservation;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class PickupCodeGeneratorTest {
    @Test void codesDeSixCaracteresLisiblesEtVaries() {
        PickupCodeGenerator generateur = new PickupCodeGenerator();
        Set<String> vus = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String code = generateur.next();
            assertThat(code).matches("[A-HJ-NP-Z2-9]{6}");
            vus.add(code);
        }
        assertThat(vus).hasSizeGreaterThan(990);
    }
}
