package ma.ensa.pharma.prescription.storage;

import ma.ensa.pharma.prescription.domain.DetectedFileType;
import ma.ensa.pharma.prescription.exception.PrescriptionException;
import ma.ensa.pharma.prescription.file.FileValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * US06 – Enregistrement sécurisé de l'ordonnance envoyée par le patient.
 * Réutilise FileValidator (RG05). ENF05 : les logs ne contiennent que l'id technique de l'ordonnance.
 */
@Service
public class PrescriptionStorageService {

    private static final Logger log = LoggerFactory.getLogger(PrescriptionStorageService.class);

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionImageRepository imageRepository;
    private final FileValidator fileValidator;
    private final ImageEncryptionService encryptionService;
    private final FamilyProfileRepository familyProfiles;

    @org.springframework.beans.factory.annotation.Autowired
    public PrescriptionStorageService(PrescriptionRepository prescriptionRepository,
                                      PrescriptionImageRepository imageRepository,
                                      FileValidator fileValidator,
                                      ImageEncryptionService encryptionService,
                                      FamilyProfileRepository familyProfiles) {
        this.prescriptionRepository = prescriptionRepository;
        this.imageRepository = imageRepository;
        this.fileValidator = fileValidator;
        this.encryptionService = encryptionService;
        this.familyProfiles = familyProfiles;
    }

    public PrescriptionStorageService(PrescriptionRepository prescriptionRepository,
                                      PrescriptionImageRepository imageRepository,
                                      FileValidator fileValidator,
                                      ImageEncryptionService encryptionService) {
        this(prescriptionRepository, imageRepository, fileValidator, encryptionService, null);
    }

    /** Enregistre l'ordonnance et son image chiffrée. Tout ou rien (@Transactional). */
    @Transactional
    public Prescription store(UUID patientId, byte[] content, String originalFilename, boolean consent) {
        return store(patientId, content, originalFilename, consent, null);
    }

    @Transactional
    public Prescription store(UUID patientId, byte[] content, String originalFilename, boolean consent, UUID profileId) {
        // 1. RG06 : consentement obligatoire
        if (!consent) {
            throw PrescriptionException.consentRequired();
        }

        // 2. RG05 : JPG / PNG / PDF, <= 5 Mo, vrai type vérifié (code TS05)
        DetectedFileType type = fileValidator.validate(content, originalFilename);

        // 3. Enregistrer l'ordonnance (statut UPLOADED)
        FamilyProfile profile = null;
        if (profileId != null) {
            if (familyProfiles == null) throw PrescriptionException.profileNotFound();
            profile = familyProfiles.findByIdAndPatientIdAndDeletedAtIsNull(profileId, patientId)
                    .orElseThrow(PrescriptionException::profileNotFound);
        }
        Prescription prescription = prescriptionRepository.save(new Prescription(patientId, type, content.length, profile));

        // 4. Chiffrer puis enregistrer l'image
        ImageEncryptionService.EncryptedData encrypted = encryptionService.encrypt(content);
        imageRepository.save(new PrescriptionImage(prescription.getId(), encrypted.iv(), encrypted.cipherText()));

        // 5. ENF05 : aucune donnée patient dans les logs
        log.info("Ordonnance {} enregistrée ({}, {} octets)", prescription.getId(), type, content.length);
        return prescription;
    }

    /** Image déchiffrée, uniquement pour le patient propriétaire. */
    @Transactional(readOnly = true)
    public StoredImage loadImage(UUID patientId, UUID prescriptionId) {
        Prescription prescription = findOwnedWithImage(patientId, prescriptionId);
        PrescriptionImage image = imageRepository.findById(prescriptionId)
                .orElseThrow(PrescriptionException::notFound);
        byte[] plain = encryptionService.decrypt(image.getIv(), image.getEncryptedData());
        return new StoredImage(prescription.mediaType(), plain);
    }

    /** Suppression de l'image à la demande du patient (droit de suppression, ENF05). */
    @Transactional
    public void deleteImage(UUID patientId, UUID prescriptionId) {
        Prescription prescription = findOwnedWithImage(patientId, prescriptionId);
        imageRepository.deleteById(prescriptionId);
        prescription.markImageDeleted();
        prescriptionRepository.save(prescription);
        log.info("Image de l'ordonnance {} supprimée", prescriptionId);
    }

    /**
     * Retrouve l'ordonnance si elle existe, appartient à ce patient et a encore son image.
     * Sinon : 404 (même réponse pour "n'existe pas" et "pas à toi", pour ne rien révéler).
     */
    private Prescription findOwnedWithImage(UUID patientId, UUID prescriptionId) {
        return prescriptionRepository.findById(prescriptionId)
                .filter(p -> p.belongsTo(patientId))
                .filter(Prescription::hasImage)
                .orElseThrow(PrescriptionException::notFound);
    }

    /** Image déchiffrée + son type MIME, pour la renvoyer au navigateur. */
    public record StoredImage(String mediaType, byte[] data) {
    }
}
