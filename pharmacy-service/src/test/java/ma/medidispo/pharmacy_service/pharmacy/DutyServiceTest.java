package ma.medidispo.pharmacy_service.pharmacy;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DutyServiceTest {

    @Test
    void todayIsComputedInMoroccoTime() {
        // Près de minuit UTC : la date dépend du décalage en vigueur au Maroc.
        // On la calcule avec le même fuseau pour ne pas dépendre de la version des règles horaires.
        Instant now = Instant.parse("2026-11-13T23:30:00Z");
        Clock clock = Clock.fixed(now, DutyService.MOROCCO);
        LocalDate expected = LocalDate.ofInstant(now, DutyService.MOROCCO);

        DutyRepository repo = mock(DutyRepository.class);
        when(repo.findOnDuty(expected)).thenReturn(List.of());

        DutyResponse response = new DutyService(repo, clock).onDutyToday(null, null);

        verify(repo).findOnDuty(expected);
        assertEquals(expected.toString(), response.date());
    }

    @Test
    void sortedByDistanceWhenLocated() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-11T10:00:00Z"), DutyService.MOROCCO);
        DutyRepository repo = mock(DutyRepository.class);

        List<DutyRepository.DutyView> views = List.of(
                view("Loin", 31.70, -8.00),
                view("Proche", 31.6260, -7.9890));
        when(repo.findOnDuty(LocalDate.of(2026, 11, 11))).thenReturn(views);

        DutyResponse response = new DutyService(repo, clock).onDutyToday(31.6258, -7.9891);

        assertEquals("Proche", response.pharmacies().get(0).name());
    }

    private static DutyRepository.DutyView view(String name, double lat, double lng) {
        DutyRepository.DutyView v = mock(DutyRepository.DutyView.class);
        when(v.getId()).thenReturn(UUID.randomUUID());
        when(v.getName()).thenReturn(name);
        when(v.getLatitude()).thenReturn(lat);
        when(v.getLongitude()).thenReturn(lng);
        when(v.getDutyUntil()).thenReturn("2026-11-15");
        return v;
    }
}
