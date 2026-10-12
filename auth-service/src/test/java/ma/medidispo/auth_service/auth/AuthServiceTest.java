package ma.medidispo.auth_service.auth;

import ma.medidispo.auth_service.exception.EmailAlreadyUsedException;
import ma.medidispo.auth_service.user.Role;
import ma.medidispo.auth_service.user.User;
import ma.medidispo.auth_service.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest request() {
        return new RegisterRequest("  Titima@Test.ma ", "Secret123", " Titima ", " Test ");
    }

    @Test
    void register_creeUnPatientAvecMotDePasseHache() {
        when(userRepository.existsByEmail("titima@test.ma")).thenReturn(false);
        when(passwordEncoder.encode("Secret123")).thenReturn("HASH");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = authService.register(request());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();

        assertEquals("titima@test.ma", saved.getEmail());
        assertEquals("HASH", saved.getPasswordHash());
        assertNotEquals("Secret123", saved.getPasswordHash());
        assertEquals("Titima", saved.getFirstName());
        assertEquals(Role.PATIENT, saved.getRole());
        assertEquals("titima@test.ma", response.email());
        assertEquals(Role.PATIENT, response.role());
    }

    @Test
    void register_refuseUnEmailDejaUtilise() {
        when(userRepository.existsByEmail("titima@test.ma")).thenReturn(true);

        assertThrows(EmailAlreadyUsedException.class, () -> authService.register(request()));

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_gereLeDoublonSimultane() {
        when(userRepository.existsByEmail("titima@test.ma")).thenReturn(false);
        when(passwordEncoder.encode("Secret123")).thenReturn("HASH");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(EmailAlreadyUsedException.class, () -> authService.register(request()));
    }
}
