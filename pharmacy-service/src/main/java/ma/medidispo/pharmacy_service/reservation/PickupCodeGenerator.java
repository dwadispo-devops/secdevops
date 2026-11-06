package ma.medidispo.pharmacy_service.reservation;

import org.springframework.stereotype.Component;
import java.security.SecureRandom;

/** RG03 : code de retrait aléatoire, lisible au comptoir (sans 0/O ni 1/I). */
@Component
public class PickupCodeGenerator {
    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int LONGUEUR = 6;
    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder code = new StringBuilder(LONGUEUR);
        for (int i = 0; i < LONGUEUR; i++) code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return code.toString();
    }
}
