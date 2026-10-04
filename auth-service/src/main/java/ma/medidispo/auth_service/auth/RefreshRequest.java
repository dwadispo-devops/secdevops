package ma.medidispo.auth_service.auth;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(

        @NotBlank(message = "Le refresh token est obligatoire")
        String refreshToken
) {
}
