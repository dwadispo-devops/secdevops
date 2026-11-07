package ma.medidispo.auth_service.internal;

import ma.medidispo.auth_service.user.User;
import ma.medidispo.auth_service.user.UserRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InternalUserControllerTest {
    private final UserRepository users = mock(UserRepository.class);
    private final UUID id = UUID.randomUUID();

    private User amina() {
        User u = new User();
        u.setEmail("amina@test.ma"); u.setFirstName("Amina"); u.setLastName("Test");
        return u;
    }

    @Test void renvoieLeContactAvecLeBonJeton() {
        when(users.findById(id)).thenReturn(Optional.of(amina()));
        var r = new InternalUserController(users, "secret-interne").contact(id, "secret-interne");
        assertEquals(200, r.getStatusCode().value());
        assertEquals("amina@test.ma", r.getBody().email());
    }

    @Test void refuseSansJetonOuAvecUnMauvaisJeton() {
        var controller = new InternalUserController(users, "secret-interne");
        assertEquals(403, controller.contact(id, null).getStatusCode().value());
        assertEquals(403, controller.contact(id, "mauvais").getStatusCode().value());
        verifyNoInteractions(users);
    }

    @Test void refuseToutSiAucunJetonNEstConfigure() {
        assertEquals(403, new InternalUserController(users, "").contact(id, "").getStatusCode().value());
    }

    @Test void neRenvoieRienPourUnCompteDesactiveOuInconnu() {
        User desactive = amina(); desactive.setEnabled(false);
        when(users.findById(id)).thenReturn(Optional.of(desactive));
        assertEquals(404, new InternalUserController(users, "s").contact(id, "s").getStatusCode().value());
    }
}
