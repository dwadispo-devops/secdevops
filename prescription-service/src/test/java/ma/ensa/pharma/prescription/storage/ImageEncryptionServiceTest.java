package ma.ensa.pharma.prescription.storage;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageEncryptionServiceTest {

    static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final ImageEncryptionService service = new ImageEncryptionService(KEY);

    @Test
    void chiffreEtDechiffre() {
        byte[] image = "contenu de l'image".getBytes(StandardCharsets.UTF_8);

        ImageEncryptionService.EncryptedData encrypted = service.encrypt(image);

        assertThat(encrypted.cipherText()).isNotEqualTo(image);
        assertThat(service.decrypt(encrypted.iv(), encrypted.cipherText())).isEqualTo(image);
    }

    @Test
    void utiliseUnIvDifferentAChaqueFois() {
        byte[] image = {1, 2, 3};
        assertThat(service.encrypt(image).iv()).isNotEqualTo(service.encrypt(image).iv());
    }

    @Test
    void detecteUneDonneeModifiee() {
        ImageEncryptionService.EncryptedData encrypted = service.encrypt(new byte[]{1, 2, 3});
        encrypted.cipherText()[0] ^= 1;   // on modifie un seul bit

        assertThatThrownBy(() -> service.decrypt(encrypted.iv(), encrypted.cipherText()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refuseUneCleDeMauvaiseTaille() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new ImageEncryptionService(shortKey)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ImageEncryptionService("pas du base64 !")).isInstanceOf(IllegalStateException.class);
    }
}
