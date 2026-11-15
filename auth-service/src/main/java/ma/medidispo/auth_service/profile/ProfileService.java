package ma.medidispo.auth_service.profile;

import ma.medidispo.auth_service.account.AccountEvent;
import ma.medidispo.auth_service.account.AccountEventRepository;
import ma.medidispo.auth_service.auth.UserResponse;
import ma.medidispo.auth_service.token.RefreshTokenRepository;
import ma.medidispo.auth_service.user.User;
import ma.medidispo.auth_service.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static ma.medidispo.auth_service.profile.ProfileDtos.*;

/** US20 / EF03 : consulter et modifier son profil, changer son mot de passe, supprimer son compte. */
@Service
public class ProfileService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AccountEventRepository accountEvents;
    private final PasswordEncoder encoder;

    public ProfileService(UserRepository users, RefreshTokenRepository refreshTokens,
                          AccountEventRepository accountEvents, PasswordEncoder encoder) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.accountEvents = accountEvents;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public UserResponse moi(UUID userId) {
        return UserResponse.from(actif(users.findById(userId).orElse(null)));
    }

    @Transactional
    public UserResponse modifier(UUID userId, UpdateProfileRequest request) {
        User user = actif(users.findByIdForUpdate(userId).orElse(null));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        return UserResponse.from(user);
    }

    /**
     * Vérifie le mot de passe actuel, enregistre le nouveau, puis révoque toutes les sessions.
     * Ordre des verrous identique à TokenService : d'abord l'utilisateur, puis ses jetons.
     */
    @Transactional
    public void changerMotDePasse(UUID userId, ChangePasswordRequest request) {
        User user = actif(users.findByIdForUpdate(userId).orElse(null));
        if (!encoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le mot de passe actuel est incorrect.");
        }
        if (encoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le nouveau mot de passe doit être différent de l'actuel.");
        }
        user.setPasswordHash(encoder.encode(request.newPassword()));
        refreshTokens.revokeAllByUserId(userId);
    }

    /**
     * US20 / loi 09-08 : droit à l'effacement.
     * Une seule transaction : sessions supprimées, compte supprimé, événement COMPTE_SUPPRIME enregistré
     * dans l'outbox. Les autres services effaceront leurs données en lisant cet événement dans Kafka.
     */
    @Transactional
    public void supprimer(UUID userId, DeleteAccountRequest request) {
        User user = actif(users.findByIdForUpdate(userId).orElse(null));
        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le mot de passe est incorrect.");
        }
        refreshTokens.deleteAllByUserId(userId);
        accountEvents.save(AccountEvent.compteSupprime(userId, user.getRole()));
        users.delete(user);
    }

    private static User actif(User user) {
        if (user == null || !user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte introuvable");
        }
        return user;
    }
}
