package ma.ensa.pharma.prescription.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import ma.ensa.pharma.prescription.api.dto.PrescriptionUploadResponse;
import ma.ensa.pharma.prescription.exception.PrescriptionException;
import ma.ensa.pharma.prescription.security.PatientJwtService;
import ma.ensa.pharma.prescription.storage.Prescription;
import ma.ensa.pharma.prescription.storage.PrescriptionStorageService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

/**
 * US06 – En tant que patient, je veux photographier ou importer mon ordonnance.
 * Contrôleur séparé de PrescriptionController (/read, TS05 / US07) pour éviter les conflits.
 */
@RestController
@RequestMapping("/api/v1/prescriptions")
@Tag(name = "Ordonnances – envoi (US06)", description = "Envoi et stockage chiffré de l'ordonnance du patient")
public class PrescriptionUploadController {

    private final PrescriptionStorageService storageService;
    private final PatientJwtService patientJwtService;

    public PrescriptionUploadController(PrescriptionStorageService storageService,
                                        PatientJwtService patientJwtService) {
        this.storageService = storageService;
        this.patientJwtService = patientJwtService;
    }

    @Operation(summary = "Envoyer une ordonnance",
            description = "Photo ou PDF (JPG, PNG, PDF, 5 Mo max). Consentement obligatoire (RG06). "
                    + "L'image est enregistrée chiffrée et l'id de l'ordonnance est renvoyé.")
    @ApiResponse(responseCode = "201", description = "Ordonnance enregistrée")
    @ApiResponse(responseCode = "400", description = "Consentement absent, fichier vide ou absent")
    @ApiResponse(responseCode = "401", description = "Non connecté ou jeton invalide")
    @ApiResponse(responseCode = "403", description = "Réservé aux patients")
    @ApiResponse(responseCode = "413", description = "Fichier trop volumineux")
    @ApiResponse(responseCode = "415", description = "Type de fichier non accepté")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PrescriptionUploadResponse> upload(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Parameter(description = "Photo ou PDF de l'ordonnance") @RequestPart("file") MultipartFile file,
            @Parameter(description = "Consentement du patient (RG06)")
            @RequestParam(name = "consent", defaultValue = "false") boolean consent,
            @RequestParam(name = "profileId", required = false) UUID profileId) {

        // 1. Qui es-tu ? (401 / 403)
        UUID patientId = patientJwtService.authenticatedPatientId(authorization);

        // 2. Consentement avant même de lire le fichier (RG06)
        if (!consent) {
            throw PrescriptionException.consentRequired();
        }

        // 3. Lire le fichier
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw PrescriptionException.unreadableImage(e);
        }

        // 4. Valider, enregistrer, chiffrer
        Prescription prescription = profileId == null
                ? storageService.store(patientId, content, file.getOriginalFilename(), true)
                : storageService.store(patientId, content, file.getOriginalFilename(), true, profileId);

        // 5. 201 Created + adresse de la nouvelle ordonnance
        return ResponseEntity
                .created(URI.create("/api/v1/prescriptions/" + prescription.getId()))
                .body(PrescriptionUploadResponse.from(prescription));
    }

    @Operation(summary = "Revoir l'image de son ordonnance")
    @ApiResponse(responseCode = "200", description = "Image déchiffrée")
    @ApiResponse(responseCode = "404", description = "Ordonnance introuvable, d'un autre patient ou image supprimée")
    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {

        UUID patientId = patientJwtService.authenticatedPatientId(authorization);
        PrescriptionStorageService.StoredImage image = storageService.loadImage(patientId, id);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.mediaType()))
                .cacheControl(CacheControl.noStore())          // donnée de santé : jamais en cache
                .header("X-Content-Type-Options", "nosniff")   // le navigateur respecte le type annoncé
                .body(image.data());
    }

    @Operation(summary = "Supprimer l'image de son ordonnance")
    @ApiResponse(responseCode = "204", description = "Image supprimée")
    @ApiResponse(responseCode = "404", description = "Ordonnance introuvable ou image déjà supprimée")
    @DeleteMapping("/{id}/image")
    public ResponseEntity<Void> deleteImage(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {

        UUID patientId = patientJwtService.authenticatedPatientId(authorization);
        storageService.deleteImage(patientId, id);
        return ResponseEntity.noContent().build();
    }
}
