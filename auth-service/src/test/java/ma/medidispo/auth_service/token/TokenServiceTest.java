package ma.medidispo.auth_service.token;

import ma.medidispo.auth_service.auth.LoginRequest;
import ma.medidispo.auth_service.auth.TokenResponse;
import ma.medidispo.auth_service.exception.InvalidCredentialsException;
import ma.medidispo.auth_service.exception.InvalidRefreshTokenException;
import ma.medidispo.auth_service.security.JwtService;
import ma.medidispo.auth_service.user.Role;
import ma.medidispo.auth_service.user.User;
import ma.medidispo.auth_service.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("secret-test-secret-test-secret-test-12345".getBytes());

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private BCryptPasswordEncoder encoder;
    private TokenService service;

    @BeforeEach
    void setUp() {
        encoder = new BCryptPasswordEncoder(4);
        service = new TokenService(userRepository, refreshTokenRepository,
                encoder, new JwtService(SECRET, 15), 7);
    }

    private User user() {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
        u.setEmail("titima@test.ma");
        u.setPasswordHash(encoder.encode("Secret123"));
        u.setRole(Role.PATIENT);
        return u;
    }

    private RefreshToken stored(User u, Instant expiresAt) {
        return new RefreshToken(u.getId(), TokenService.sha256("raw-token"), expiresAt);
    }

    @Test
    void login_ok_renvoieLesDeuxTokens_etNeStockeQueLEmpreinte() {
        User u = user();
        when(userRepository.findByEmail("titima@test.ma")).thenReturn(Optional.of(u));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        TokenResponse response = service.login(new LoginRequest(" Titima@Test.ma ", "Secret123"));

        assertNotNull(response.accessToken());
        assertNotNull(response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(900, response.expiresIn());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertNotEquals(response.refreshToken(), captor.getValue().getTokenHash());
        assertEquals(TokenService.sha256(response.refreshToken()), captor.getValue().getTokenHash());
    }

    @Test
    void login_motDePasseIncorrect_estRefuse() {
        when(userRepository.findByEmail("titima@test.ma")).thenReturn(Optional.of(user()));

        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("titima@test.ma", "MauvaisMdp1")));

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void login_emailInconnu_memeErreurQuUnMauvaisMotDePasse() {
        when(userRepository.findByEmail("inconnu@test.ma")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("inconnu@test.ma", "Secret123")));
    }

    @Test
    void refresh_ok_annuleLAncienRecu_etEnDonneUnNouveau() {
        User u = user();
        RefreshToken old = stored(u, Instant.now().plus(1, ChronoUnit.DAYS));
        when(refreshTokenRepository.findByTokenHash(TokenService.sha256("raw-token"))).thenReturn(Optional.of(old));
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        TokenResponse response = service.refresh("raw-token");

        assertTrue(old.isRevoked());
        assertNotNull(response.accessToken());
        assertNotEquals("raw-token", response.refreshToken());
    }

    @Test
    void refresh_recuDejaUtilise_declencheLAlarmeEtAnnuleTout() {
        User u = user();
        RefreshToken used = stored(u, Instant.now().plus(1, ChronoUnit.DAYS));
        used.setRevoked(true);
        when(refreshTokenRepository.findByTokenHash(TokenService.sha256("raw-token"))).thenReturn(Optional.of(used));

        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh("raw-token"));

        verify(refreshTokenRepository).revokeAllByUserId(u.getId());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refresh_recuExpire_estRefuse() {
        User u = user();
        RefreshToken expired = stored(u, Instant.now().minus(1, ChronoUnit.DAYS));
        when(refreshTokenRepository.findByTokenHash(TokenService.sha256("raw-token"))).thenReturn(Optional.of(expired));

        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh("raw-token"));

        verify(refreshTokenRepository, never()).save(any());
    }
}
