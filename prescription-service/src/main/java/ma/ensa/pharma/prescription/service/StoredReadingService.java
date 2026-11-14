package ma.ensa.pharma.prescription.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.ensa.pharma.prescription.api.dto.ReadPrescriptionResponse;
import ma.ensa.pharma.prescription.api.dto.PrescriptionHistoryResponse;
import ma.ensa.pharma.prescription.exception.PrescriptionException;
import ma.ensa.pharma.prescription.storage.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class StoredReadingService {
    private final PrescriptionRepository repository;
    private final PrescriptionStorageService storage;
    private final PrescriptionReadingService reader;
    private final ImageEncryptionService encryption;
    private final ObjectMapper mapper;
    public StoredReadingService(PrescriptionRepository repository, PrescriptionStorageService storage,
                                PrescriptionReadingService reader, ImageEncryptionService encryption, ObjectMapper mapper) {
        this.repository=repository; this.storage=storage; this.reader=reader; this.encryption=encryption; this.mapper=mapper;
    }
    private Prescription owned(UUID patient, UUID id) {
        return repository.findById(id).filter(p -> p.belongsTo(patient)).orElseThrow(PrescriptionException::notFound);
    }
    @Transactional(readOnly=true)
    public List<PrescriptionHistoryResponse> history(UUID patient) {
        return repository.findAllByPatientIdOrderByCreatedAtDesc(patient).stream()
                .map(PrescriptionHistoryResponse::from)
                .toList();
    }

    @Transactional
    public ReadPrescriptionResponse read(UUID patient, UUID id, boolean consent) {
        if (!consent) throw PrescriptionException.consentRequired();
        // Serialize deletion and reading persistence so deleted data cannot reappear.
        repository.lockById(id).filter(p -> p.belongsTo(patient) && p.hasImage()).orElseThrow(PrescriptionException::notFound);
        var image=storage.loadImage(patient,id);
        var extension=switch(image.mediaType()) { case "image/png" -> ".png"; case "image/jpeg" -> ".jpg"; default -> ".pdf"; };
        // Reuses US07. If the real model is unavailable no result is saved or fabricated.
        var result=reader.read(image.data(), "prescription"+extension, true);
        var prescription=owned(patient,id);
        if (!prescription.hasImage()) throw PrescriptionException.notFound();
        try {
            var encrypted=encryption.encrypt(mapper.writeValueAsBytes(result));
            prescription.storeReading(encrypted.iv(), encrypted.cipherText());
            repository.save(prescription);
        } catch (java.io.IOException ex) { throw new IllegalStateException("Lecture non enregistrable", ex); }
        return result;
    }
    @Transactional(readOnly=true)
    public View view(UUID patient, UUID id) {
        var p=owned(patient,id);
        if (!p.hasImage() || p.getReadingData()==null) return new View(p.hasImage(),p.mediaType(),null,List.of());
        try {
            var result=mapper.readValue(encryption.decrypt(p.getReadingIv(),p.getReadingData()),ReadPrescriptionResponse.class);
            var lines = result.lignes() == null ? List.<ma.ensa.pharma.prescription.api.dto.LigneOrdonnanceDto>of() : result.lignes();
            var text = lines.stream().map(l -> l.texteLu()).filter(Objects::nonNull).collect(java.util.stream.Collectors.joining("\n"));
            return new View(p.hasImage(), p.mediaType(), text.isBlank() ? null : text,
                lines.stream().map(l -> new Medication(l.texteLu(), bestMatch(l), null, statusOf(l.score()))).toList());
        } catch (java.io.IOException ex) { throw new IllegalStateException("Lecture enregistrée illisible", ex); }
    }
    private static String bestMatch(ma.ensa.pharma.prescription.api.dto.LigneOrdonnanceDto l) {
        return l.suggestions() == null || l.suggestions().isEmpty() ? null : l.suggestions().get(0).nom();
    }

    /** Seuils du cahier des charges (5.1) : >= 0,85 vert, 0,60 a 0,85 orange, < 0,60 rouge. */
    private static String statusOf(double score) {
        if (score >= 0.85) return "LU";
        if (score >= 0.60) return "INCERTAIN";
        return "ILLISIBLE";
    }

    public record Medication(String rawText,String name,String dosage,String status) {}
    public record View(boolean imageAvailable,String mediaType,String ocrText,List<Medication> medications) {}
}
