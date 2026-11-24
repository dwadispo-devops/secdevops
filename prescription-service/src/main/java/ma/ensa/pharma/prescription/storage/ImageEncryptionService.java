package ma.ensa.pharma.prescription.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Chiffrement AES-256-GCM des images d'ordonnance (RG06, ENF05).
 * Clé : 32 octets en Base64, fournie par la variable d'environnement PRESCRIPTION_IMAGE_KEY
 * (fichier .env en local, secret GitHub Actions en CI). Jamais dans le code.
 * Générer une clé : openssl rand -base64 32
 */
@Service
public class ImageEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;          // taille recommandée pour GCM
    private static final int TAG_LENGTH_BITS = 128;   // taille du "sceau" anti-modification

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public ImageEncryptionService(@Value("${prescription.storage.encryption-key}") String base64Key) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("PRESCRIPTION_IMAGE_KEY n'est pas en Base64 valide", e);
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("PRESCRIPTION_IMAGE_KEY doit faire 32 octets (openssl rand -base64 32)");
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    /** Chiffre l'image avec un IV aléatoire neuf. */
    public EncryptedData encrypt(byte[] plain) {
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new EncryptedData(iv, cipher.doFinal(plain));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Échec du chiffrement de l'image", e);
        }
    }

    /** Déchiffre l'image ; échoue si les données ont été modifiées ou si la clé est mauvaise. */
    public byte[] decrypt(byte[] iv, byte[] cipherText) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return cipher.doFinal(cipherText);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Échec du déchiffrement de l'image", e);
        }
    }

    /** Résultat du chiffrement : l'IV (non secret) + les données chiffrées. */
    public record EncryptedData(byte[] iv, byte[] cipherText) {
    }
}
