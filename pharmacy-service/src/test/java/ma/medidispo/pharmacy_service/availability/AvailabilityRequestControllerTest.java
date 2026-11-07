package ma.medidispo.pharmacy_service.availability;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AvailabilityRequestControllerTest {

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "NaN,0", "Infinity,0", "-Infinity,0", "0,NaN", "0,Infinity", "0,-Infinity"
    })
    void nonFiniteCoordinatesAreRejectedBeforePersistence(double latitude, double longitude) {
        var repository = mock(AvailabilityRequestRepository.class);
        var items = mock(AvailabilityRequestItemRepository.class);
        var jwt = mock(AvailabilityJwtService.class);
        var search = mock(AvailabilityPharmacySearchService.class);
        var associations = mock(AvailabilityRequestPharmacyRepository.class);
        when(jwt.authenticatedPatientId("Bearer patient"))
                .thenReturn(java.util.UUID.randomUUID());
        var controller = new AvailabilityRequestController(org.mockito.Mockito.mock(ma.medidispo.pharmacy_service.notification.NotificationService.class), repository, items, jwt, search, associations);
        var response = controller.create("Bearer patient",
                new AvailabilityRequestController.CreateRequest(latitude, longitude, 100,
                        java.util.List.of(new AvailabilityRequestController.Medication("Doliprane", 1))));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(repository, items, search, associations);
    }

    @Test
    void requestWithoutJwtIsRejected() {
        AvailabilityRequestRepository repository = mock(AvailabilityRequestRepository.class);
        AvailabilityRequestItemRepository itemRepository = mock(AvailabilityRequestItemRepository.class);
        AvailabilityJwtService jwtService = mock(AvailabilityJwtService.class);

        when(jwtService.authenticatedPatientId(null))
                .thenThrow(new SecurityException("Token Bearer manquant"));

        AvailabilityRequestController controller =
                new AvailabilityRequestController(org.mockito.Mockito.mock(ma.medidispo.pharmacy_service.notification.NotificationService.class), repository, itemRepository, jwtService, mock(AvailabilityPharmacySearchService.class), mock(AvailabilityRequestPharmacyRepository.class));

        ResponseEntity<?> response = controller.create(
                null,
                new AvailabilityRequestController.CreateRequest(
                        31.63, -8.0, 2000,
                        java.util.List.of(
                                new AvailabilityRequestController.Medication("Doliprane", 2)
                        )
                )
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(repository, itemRepository);
    }

    @Test
    void validPatientCanCreateRequestWithMedications() {
        AvailabilityRequestRepository repository = mock(AvailabilityRequestRepository.class);
        AvailabilityRequestItemRepository itemRepository = mock(AvailabilityRequestItemRepository.class);
        AvailabilityJwtService jwtService = mock(AvailabilityJwtService.class);

        java.util.UUID patientId = java.util.UUID.randomUUID();

        when(jwtService.authenticatedPatientId("Bearer valid-token"))
                .thenReturn(patientId);

        when(repository.save(any(AvailabilityRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AvailabilityRequestController controller =
                new AvailabilityRequestController(org.mockito.Mockito.mock(ma.medidispo.pharmacy_service.notification.NotificationService.class), repository, itemRepository, jwtService, mock(AvailabilityPharmacySearchService.class), mock(AvailabilityRequestPharmacyRepository.class));

        ResponseEntity<?> response = controller.create(
                "Bearer valid-token",
                new AvailabilityRequestController.CreateRequest(
                        31.63, -8.0, 2000,
                        java.util.List.of(
                                new AvailabilityRequestController.Medication("Doliprane", 2)
                        )
                )
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(repository).save(any(AvailabilityRequest.class));
        verify(itemRepository).save(argThat(item ->
                item.getMedicationName().equals("Doliprane") && item.getQuantity() == 2));
    }

    @Test
    void invalidJwtIsRejected() {
        assertRejectedToken(
                new io.jsonwebtoken.security.SignatureException("Signature invalide")
        );
    }

    @Test
    void expiredJwtIsRejected() {
        assertRejectedToken(
                new io.jsonwebtoken.ExpiredJwtException(null, null, "Token expire")
        );
    }

    @Test
    void wrongRoleIsRejected() {
        assertRejectedToken(
                new SecurityException("Acces reserve aux patients")
        );
    }

    private void assertRejectedToken(RuntimeException error) {
        AvailabilityRequestRepository repository =
                mock(AvailabilityRequestRepository.class);
        AvailabilityRequestItemRepository itemRepository =
                mock(AvailabilityRequestItemRepository.class);
        AvailabilityJwtService jwtService =
                mock(AvailabilityJwtService.class);

        when(jwtService.authenticatedPatientId("Bearer bad-token"))
                .thenThrow(error);

        AvailabilityRequestController controller =
                new AvailabilityRequestController(org.mockito.Mockito.mock(ma.medidispo.pharmacy_service.notification.NotificationService.class), 
                        repository,
                        itemRepository,
                        jwtService,
                        mock(AvailabilityPharmacySearchService.class),
                        mock(AvailabilityRequestPharmacyRepository.class)
                );

        ResponseEntity<?> response = controller.create(
                "Bearer bad-token",
                new AvailabilityRequestController.CreateRequest(
                        31.63, -8.0, 2000,
                        java.util.List.of(
                                new AvailabilityRequestController.Medication("Doliprane", 2)
                        )
                )
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(repository, itemRepository);
    }
}
