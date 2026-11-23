package ma.medidispo.auth_service.profile;

import ma.medidispo.auth_service.account.AccountEvent;
import ma.medidispo.auth_service.account.AccountEventRepository;
import ma.medidispo.auth_service.security.JwtService;
import ma.medidispo.auth_service.token.RefreshTokenRepository;
import ma.medidispo.auth_service.user.Role;
import ma.medidispo.auth_service.user.User;
import ma.medidispo.auth_service.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US20 / loi 09-08 : suppression du compte et événement COMPTE_SUPPRIME dans l'outbox. */
@SpringBootTest @AutoConfigureMockMvc
class AccountDeletionTest {
    static final String MOT_DE_PASSE = "Secret123";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired AccountEventRepository events;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;

    User creer(Role role) {
        User u = new User();
        u.setEmail("compte-" + UUID.randomUUID() + "@test.ma");
        u.setPasswordHash(encoder.encode(MOT_DE_PASSE));
        u.setFirstName("Amina"); u.setLastName("Test");
        u.setRole(role);
        return users.saveAndFlush(u);
    }

    void login(User u) throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + u.getEmail() + "\",\"password\":\"" + MOT_DE_PASSE + "\"}"))
            .andExpect(status().isOk());
    }

    @Test void supprimeLeCompteLesSessionsEtPrepareLEvenement() throws Exception {
        User u = creer(Role.PATIENT);
        login(u);
        String bearer = "Bearer " + jwt.generateAccessToken(u);

        mvc.perform(post("/api/auth/me/suppression").header("Authorization", bearer)
            .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + MOT_DE_PASSE + "\"}"))
            .andExpect(status().isNoContent());

        assertThat(users.findById(u.getId())).isEmpty();
        assertThat(refreshTokens.findAll()).noneMatch(t -> t.getUserId().equals(u.getId()));
        mvc.perform(get("/api/auth/me").header("Authorization", bearer)).andExpect(status().isNotFound());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + u.getEmail() + "\",\"password\":\"" + MOT_DE_PASSE + "\"}"))
            .andExpect(status().isUnauthorized());

        var e = events.findByUserId(u.getId());
        assertThat(e).hasSize(1);
        assertThat(e.get(0).getType()).isEqualTo(AccountEvent.COMPTE_SUPPRIME);
        assertThat(e.get(0).getRole()).isEqualTo("PATIENT");
        assertThat(e.get(0).getPublishedAt()).isNull();
    }

    @Test void refuseUnMotDePasseIncorrectEtNeSupprimeRien() throws Exception {
        User u = creer(Role.PHARMACIEN);
        mvc.perform(post("/api/auth/me/suppression").header("Authorization", "Bearer " + jwt.generateAccessToken(u))
            .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"Faux0000\"}"))
            .andExpect(status().isBadRequest());
        assertThat(users.findById(u.getId())).isPresent();
        assertThat(events.findByUserId(u.getId())).isEmpty();
    }

    @Test void refuseSansJeton() throws Exception {
        mvc.perform(post("/api/auth/me/suppression").contentType(MediaType.APPLICATION_JSON)
            .content("{\"password\":\"" + MOT_DE_PASSE + "\"}"))
            .andExpect(status().isUnauthorized());
    }
}
