package ma.medidispo.auth_service.token;

import ma.medidispo.auth_service.auth.LoginRequest;
import ma.medidispo.auth_service.auth.TokenResponse;
import ma.medidispo.auth_service.exception.InvalidCredentialsException;
import ma.medidispo.auth_service.exception.InvalidRefreshTokenException;
import ma.medidispo.auth_service.security.JwtService;
import ma.medidispo.auth_service.user.User;
import ma.medidispo.auth_service.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Service
public class TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshTtlDays;
    private final String dummyHash;

    public TokenService(UserRepository userRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        @Value("${app.jwt.refresh-ttl-days}") long refreshTtlDays) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTtlDays = refreshTtlDays;
        // Faux hash : on fait quand même un calcul BCrypt si l'e-mail n'existe pas,
        // pour qu'on ne puisse pas deviner les e-mails d'après le temps de réponse.
        this.dummyHash = passwordEncoder.encode("mot-de-passe-factice");
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Optional<User> found = userRepository.findByEmail(email);

        String hash = found.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordOk = passwordEncoder.matches(request.password(), hash);

        if (found.isEmpty() || !passwordOk || !found.get().isEnabled()) {
            throw new InvalidCredentialsException();
        }
        return issueTokens(found.get());
    }

    // noRollbackFor : l'annulation de tous les reçus (alarme anti-vol)
    // doit être enregistrée même si on lève l'exception juste après.
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenResponse refresh(String rawRefreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (stored.isRevoked()) {
            // Reçu déjà utilisé : il a sans doute été copié -> on annule tout
            refreshTokenRepository.revokeAllByUserId(stored.getUserId());
            throw new InvalidRefreshTokenException();
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }

        User user = userRepository.findById(stored.getUserId())
                .filter(User::isEnabled)
                .orElseThrow(InvalidRefreshTokenException::new);

        stored.setRevoked(true); // rotation : l'ancien reçu est annulé
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(sha256(rawRefreshToken))
                .ifPresent(t -> t.setRevoked(true));
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawRefresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        refreshTokenRepository.save(new RefreshToken(
                user.getId(),
                sha256(rawRefresh),
                Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS)));

        return new TokenResponse(accessToken, rawRefresh, "Bearer", jwtService.getAccessTtlSeconds());
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
