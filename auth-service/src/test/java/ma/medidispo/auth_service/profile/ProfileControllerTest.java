package ma.medidispo.auth_service.profile;

import ma.medidispo.auth_service.security.JwtService;
import ma.medidispo.auth_service.user.Role;
import ma.medidispo.auth_service.user.User;
import ma.medidispo.auth_service.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Filtre JWT d'auth-service et profil (US20) : consulter, modifier, changer le mot de passe. */
@SpringBootTest @AutoConfigureMockMvc
class ProfileControllerTest {
    static final String MOT_DE_PASSE = "Secret123";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;

    User creer(boolean actif) {
        User u = new User();
        u.setEmail("amina-" + UUID.randomUUID() + "@test.ma");
        u.setPasswordHash(encoder.encode(MOT_DE_PASSE));
        u.setFirstName("Amina"); u.setLastName("Test");
        u.setRole(Role.PATIENT); u.setEnabled(actif);
        return users.saveAndFlush(u);
    }
    String bearer(User u) { return "Bearer " + jwt.generateAccessToken(u); }

    ResultActions json(MockHttpServletRequestBuilder r, String body) throws Exception {
        return mvc.perform(r.contentType(MediaType.APPLICATION_JSON).content(body));
    }
    ResultActions login(String email, String mdp) throws Exception {
        return json(post("/api/auth/login"), "{\"email\":\"" + email + "\",\"password\":\"" + mdp + "\"}");
    }
    String refreshTokenDe(ResultActions r) throws Exception {
        Map<String, Object> m = JsonParserFactory.getJsonParser().parseMap(r.andReturn().getResponse().getContentAsString());
        return m.entrySet().stream().filter(e -> e.getKey().toLowerCase().contains("refresh"))
                .map(e -> String.valueOf(e.getValue())).findFirst().orElseThrow();
    }

    // ---------- Consulter (filtre JWT) ----------

    @Test void renvoieMonProfilAvecUnJetonValide() throws Exception {
        User u = creer(true);
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(u)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(u.getEmail()))
            .andExpect(jsonPath("$.firstName").value("Amina"))
            .andExpect(jsonPath("$.role").value("PATIENT"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test void refuseSansJeton() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test void refuseUnJetonFalsifie() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer abc.def.ghi"))
            .andExpect(status().isUnauthorized());
    }

    @Test void unCompteDesactiveNEstPlusVisible() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(creer(false))))
            .andExpect(status().isNotFound());
    }

    @Test void lesRoutesPubliquesRestentAccessiblesSansJeton() throws Exception {
        json(post("/api/auth/register"), "{}").andExpect(status().isBadRequest());
    }

    // ---------- Modifier le profil ----------

    @Test void modifieLePrenomEtLeNom() throws Exception {
        User u = creer(true);
        json(put("/api/auth/me").header("Authorization", bearer(u)), "{\"firstName\":\"  Salma \",\"lastName\":\"Bennani\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value("Salma"))
            .andExpect(jsonPath("$.lastName").value("Bennani"));
        User relu = users.findById(u.getId()).orElseThrow();
        assertThat(relu.getFirstName()).isEqualTo("Salma");
        assertThat(relu.getEmail()).isEqualTo(u.getEmail());
    }

    @Test void refuseUnPrenomVide() throws Exception {
        json(put("/api/auth/me").header("Authorization", bearer(creer(true))), "{\"firstName\":\" \",\"lastName\":\"Test\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.firstName").exists());
    }

    // ---------- Changer le mot de passe ----------

    @Test void changeLeMotDePasseEtRevoqueLesSessions() throws Exception {
        User u = creer(true);
        String ancienRefresh = refreshTokenDe(login(u.getEmail(), MOT_DE_PASSE).andExpect(status().isOk()));

        json(put("/api/auth/me/password").header("Authorization", bearer(u)),
             "{\"currentPassword\":\"" + MOT_DE_PASSE + "\",\"newPassword\":\"Nouveau456\"}")
            .andExpect(status().isNoContent());

        login(u.getEmail(), MOT_DE_PASSE).andExpect(status().isUnauthorized());
        login(u.getEmail(), "Nouveau456").andExpect(status().isOk());
        json(post("/api/auth/refresh"), "{\"refreshToken\":\"" + ancienRefresh + "\"}")
            .andExpect(status().isUnauthorized());
    }

    @Test void refuseUnMotDePasseActuelIncorrect() throws Exception {
        json(put("/api/auth/me/password").header("Authorization", bearer(creer(true))),
             "{\"currentPassword\":\"Faux0000\",\"newPassword\":\"Nouveau456\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Le mot de passe actuel est incorrect."));
    }

    @Test void refuseUnNouveauMotDePasseFaibleOuIdentique() throws Exception {
        User u = creer(true);
        json(put("/api/auth/me/password").header("Authorization", bearer(u)),
             "{\"currentPassword\":\"" + MOT_DE_PASSE + "\",\"newPassword\":\"court\"}")
            .andExpect(status().isBadRequest());
        json(put("/api/auth/me/password").header("Authorization", bearer(u)),
             "{\"currentPassword\":\"" + MOT_DE_PASSE + "\",\"newPassword\":\"" + MOT_DE_PASSE + "\"}")
            .andExpect(status().isBadRequest());
    }
}
