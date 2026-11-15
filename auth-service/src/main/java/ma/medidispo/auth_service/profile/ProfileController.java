package ma.medidispo.auth_service.profile;

import jakarta.validation.Valid;
import ma.medidispo.auth_service.auth.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static ma.medidispo.auth_service.profile.ProfileDtos.*;

/** US20 / EF03 : profil de l'utilisateur connecté. L'identifiant vient du JWT, jamais de la requête. */
@RestController
@RequestMapping("/api/auth/me")
public class ProfileController {

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping
    public UserResponse moi(Authentication auth) {
        return service.moi(id(auth));
    }

    @PutMapping
    public UserResponse modifier(Authentication auth, @Valid @RequestBody UpdateProfileRequest request) {
        return service.modifier(id(auth), request);
    }

    /** Toutes les sessions sont révoquées : l'utilisateur doit se reconnecter avec le nouveau mot de passe. */
    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changerMotDePasse(Authentication auth, @Valid @RequestBody ChangePasswordRequest request) {
        service.changerMotDePasse(id(auth), request);
    }

    /** US20 / loi 09-08 : suppression définitive du compte, confirmée par le mot de passe. */
    @PostMapping("/suppression")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(Authentication auth, @Valid @RequestBody DeleteAccountRequest request) {
        service.supprimer(id(auth), request);
    }

    private static UUID id(Authentication auth) {
        return UUID.fromString(auth.getName());
    }
}
