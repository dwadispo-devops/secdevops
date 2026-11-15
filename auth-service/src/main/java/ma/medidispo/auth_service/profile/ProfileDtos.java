package ma.medidispo.auth_service.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** US20 : corps des requêtes de modification du profil. */
public final class ProfileDtos {
    private ProfileDtos() {}

    public record UpdateProfileRequest(
            @NotBlank(message = "Le prénom est obligatoire")
            @Size(max = 100, message = "Le prénom est trop long")
            String firstName,

            @NotBlank(message = "Le nom est obligatoire")
            @Size(max = 100, message = "Le nom est trop long")
            String lastName
    ) {}

    /** Mêmes règles que l'inscription pour le nouveau mot de passe. */
    public record ChangePasswordRequest(
            @NotBlank(message = "Le mot de passe actuel est obligatoire")
            String currentPassword,

            @NotBlank(message = "Le nouveau mot de passe est obligatoire")
            @Size(min = 8, max = 72, message = "Le mot de passe doit contenir entre 8 et 72 caractères")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                     message = "Le mot de passe doit contenir au moins une lettre et un chiffre")
            String newPassword
    ) {}
    /** US20 : suppression du compte, confirmée par le mot de passe. */
    public record DeleteAccountRequest(
            @NotBlank(message = "Le mot de passe est obligatoire")
            String password
    ) {}
}
