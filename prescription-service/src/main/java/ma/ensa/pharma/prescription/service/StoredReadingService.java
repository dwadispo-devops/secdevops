package ma.ensa.pharma.prescription.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.ensa.pharma.prescription.api.dto.ReadPrescriptionResponse;
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
            var lines=result.lines();
            var text=lines.stream().map(l -> l.rawText()).filter(Objects::nonNull).collect(java.util.stream.Collectors.joining("\n"));
            return new View(p.hasImage(),p.mediaType(),text.isBlank()?null:text,
                lines.stream().map(l -> new Medication(l.rawText(),l.name(),l.dosage(),l.status().name())).toList());
        } catch (java.io.IOException ex) { throw new IllegalStateException("Lecture enregistrée illisible", ex); }
    }
    public record Medication(String rawText,String name,String dosage,String status) {}
    public record View(boolean imageAvailable,String mediaType,String ocrText,List<Medication> medications) {}
}
