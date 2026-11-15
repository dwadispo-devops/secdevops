package ma.medidispo.auth_service.profile;

import ma.medidispo.auth_service.security.JwtService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Filtre JWT d'auth-service et GET /api/auth/me (US20). */
@SpringBootTest @AutoConfigureMockMvc
class ProfileControllerTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;

    User creer(boolean actif) {
        User u = new User();
        u.setEmail("amina-" + UUID.randomUUID() + "@test.ma");
        u.setPasswordHash(encoder.encode("Secret123"));
        u.setFirstName("Amina"); u.setLastName("Test");
        u.setRole(Role.PATIENT); u.setEnabled(actif);
        return users.saveAndFlush(u);
    }

    @Test void renvoieMonProfilAvecUnJetonValide() throws Exception {
        User u = creer(true);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jwt.generateAccessToken(u)))
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
        User u = creer(false);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jwt.generateAccessToken(u)))
            .andExpect(status().isNotFound());
    }

    @Test void lesRoutesPubliquesRestentAccessiblesSansJeton() throws Exception {
        // Corps invalide : la requête atteint bien le contrôleur (400), elle n'est pas bloquée par la sécurité
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }
}
