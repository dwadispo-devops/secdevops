package ma.medidispo.auth_service.auth;

import ma.medidispo.auth_service.exception.EmailAlreadyUsedException;
import ma.medidispo.auth_service.user.Role;
import ma.medidispo.auth_service.user.User;
import ma.medidispo.auth_service.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse register(RegisterRequest request) {
        return register(request, Role.PATIENT);
    }

    /** US03 : compte pharmacien. Sa pharmacie reste « en attente » tant qu'un administrateur ne l'a pas validée (EF06). */
    public UserResponse registerPharmacist(RegisterRequest request) {
        return register(request, Role.PHARMACIEN);
    }

    private UserResponse register(RegisterRequest request, Role role) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setRole(role);

        try {
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            // deux inscriptions simultanées avec le même e-mail
            throw new EmailAlreadyUsedException();
        }
    }
}
