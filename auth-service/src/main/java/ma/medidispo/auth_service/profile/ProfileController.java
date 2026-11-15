package ma.medidispo.auth_service.profile;

import ma.medidispo.auth_service.auth.UserResponse;
import ma.medidispo.auth_service.user.User;
import ma.medidispo.auth_service.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** US20 / EF03 : profil de l'utilisateur connecté. L'identifiant vient du JWT, jamais de la requête. */
@RestController
@RequestMapping("/api/auth/me")
public class ProfileController {

    private final UserRepository users;

    public ProfileController(UserRepository users) {
        this.users = users;
    }

    @GetMapping
    public UserResponse me(Authentication auth) {
        return users.findById(UUID.fromString(auth.getName()))
                .filter(User::isEnabled)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte introuvable"));
    }
}
