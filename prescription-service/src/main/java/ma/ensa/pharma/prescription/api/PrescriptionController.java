package ma.ensa.pharma.prescription.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import ma.ensa.pharma.prescription.api.dto.ReadPrescriptionResponse;
import ma.ensa.pharma.prescription.exception.PrescriptionException;
import ma.ensa.pharma.prescription.service.PrescriptionReadingService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/prescriptions")
@Tag(name = "Ordonnances", description = "Lecture d'ordonnances par IA vision (PoC TS05)")
public class PrescriptionController {

    private final PrescriptionReadingService readingService;

    public PrescriptionController(PrescriptionReadingService readingService) {
        this.readingService = readingService;
    }

    @Operation(summary = "Lire une ordonnance",
            description = "Envoie une photo ou un PDF d'ordonnance (JPG, PNG, PDF, 5 Mo max) et renvoie "
                    + "la liste des médicaments lus. Le consentement du patient est obligatoire (RG06).")
    @ApiResponse(responseCode = "200", description = "Lecture effectuée")
    @ApiResponse(responseCode = "400", description = "Consentement absent ou fichier vide")
    @ApiResponse(responseCode = "413", description = "Fichier trop volumineux")
    @ApiResponse(responseCode = "415", description = "Type de fichier non accepté")
    @ApiResponse(responseCode = "422", description = "Fichier illisible")
    @ApiResponse(responseCode = "503", description = "Modèle vision indisponible : proposer la saisie manuelle")
    @PostMapping(value = "/read", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ReadPrescriptionResponse read(
            @Parameter(description = "Photo ou PDF de l'ordonnance") @RequestPart("file") MultipartFile file,
            @Parameter(description = "Consentement du patient à l'envoi de l'image à l'IA")
            @RequestParam(name = "consent", defaultValue = "false") boolean consent) {
        if (!consent) {
            // Refus avant même de lire le contenu du fichier
            throw PrescriptionException.consentRequired();
        }
        try {
            return readingService.read(file.getBytes(), file.getOriginalFilename(), true);
        } catch (IOException e) {
            throw PrescriptionException.unreadableImage(e);
        }
    }
}
