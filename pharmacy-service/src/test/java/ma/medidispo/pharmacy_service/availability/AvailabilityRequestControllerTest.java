package ma.medidispo.pharmacy_service.availability;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AvailabilityRequestControllerTest {

    @Test
    void requestWithoutJwtIsRejected() {
        AvailabilityRequestRepository repository = mock(AvailabilityRequestRepository.class);
        AvailabilityRequestItemRepository itemRepository = mock(AvailabilityRequestItemRepository.class);
        AvailabilityJwtService jwtService = mock(AvailabilityJwtService.class);

        when(jwtService.authenticatedPatientId(null))
                .thenThrow(new SecurityException("Token Bearer manquant"));

        AvailabilityRequestController controller =
                new AvailabilityRequestController(repository, itemRepository, jwtService);

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
                new AvailabilityRequestController(repository, itemRepository, jwtService);

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
}