package ma.medidispo.pharmacy_service.review;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import static ma.medidispo.pharmacy_service.review.ReviewDtos.*;

@RestController
public class ReviewController {
    private final ReviewService service;
    public ReviewController(ReviewService service) { this.service=service; }
    private UUID user(Authentication auth) { return (UUID)auth.getPrincipal(); }
    @GetMapping("/api/pharmacies/me/requests")
    public List<Request> inbox(Authentication auth) { return service.inbox(user(auth)); }
    @GetMapping("/api/pharmacies/{pharmacy}/requests/{request}")
    public Detail detail(Authentication auth, @PathVariable UUID pharmacy, @PathVariable UUID request) {
        return service.detail(user(auth), pharmacy, request);
    }
    @PostMapping("/api/pharmacies/{pharmacy}/requests/{request}/response")
    public Request respond(Authentication auth, @PathVariable UUID pharmacy, @PathVariable UUID request,
                           @Valid @RequestBody Response response) {
        return service.respond(user(auth), pharmacy, request, response);
    }
    @GetMapping("/api/pharmacies/{pharmacy}/requests/{request}/prescription")
    public Prescription prescription(Authentication auth, @PathVariable UUID pharmacy, @PathVariable UUID request) {
        return service.prescription(user(auth), pharmacy, request);
    }
    @GetMapping("/api/pharmacies/{pharmacy}/requests/{request}/prescription/image")
    public ResponseEntity<byte[]> image(Authentication auth, @PathVariable UUID pharmacy, @PathVariable UUID request) {
        var image=service.image(user(auth), pharmacy, request);
        return ResponseEntity.ok().contentType(image.getHeaders().getContentType())
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(image.getBody());
    }
    @PutMapping("/api/availability-requests/{request}/prescription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void link(Authentication auth, @PathVariable UUID request, @Valid @RequestBody Link body) {
        service.link(user(auth), request, body.prescriptionId());
    }
    @GetMapping("/api/availability-requests/{request}/responses")
    public List<Request> responses(Authentication auth, @PathVariable UUID request) {
        return service.responses(user(auth), request);
    }
}
