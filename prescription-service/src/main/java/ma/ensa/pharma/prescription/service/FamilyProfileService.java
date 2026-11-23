package ma.ensa.pharma.prescription.service;

import ma.ensa.pharma.prescription.exception.PrescriptionException;
import ma.ensa.pharma.prescription.storage.FamilyProfile;
import ma.ensa.pharma.prescription.storage.FamilyProfileRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class FamilyProfileService {
    private final FamilyProfileRepository repository;

    public FamilyProfileService(FamilyProfileRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true)
    public List<FamilyProfile> list(UUID patientId) {
        return repository.findAllByPatientIdAndDeletedAtIsNullOrderByCreatedAtAsc(patientId);
    }

    @Transactional
    public FamilyProfile create(UUID patientId, String label) {
        String normalized = normalize(label);
        ensureUnique(patientId, normalized);
        try { return repository.saveAndFlush(new FamilyProfile(patientId, normalized)); }
        catch (DataIntegrityViolationException ex) { throw PrescriptionException.profileLabelConflict(); }
    }

    @Transactional
    public FamilyProfile rename(UUID patientId, UUID id, String label) {
        String normalized = normalize(label);
        FamilyProfile profile = owned(patientId, id);
        if (repository.existsByPatientIdAndDeletedAtIsNullAndLabelIgnoreCaseAndIdNot(patientId, normalized, id)) {
            throw PrescriptionException.profileLabelConflict();
        }
        profile.rename(normalized);
        try { return repository.saveAndFlush(profile); }
        catch (DataIntegrityViolationException ex) { throw PrescriptionException.profileLabelConflict(); }
    }

    @Transactional
    public void hide(UUID patientId, UUID id) { owned(patientId, id).hide(); }

    private FamilyProfile owned(UUID patientId, UUID id) {
        return repository.findByIdAndPatientIdAndDeletedAtIsNull(id, patientId)
                .orElseThrow(PrescriptionException::profileNotFound);
    }

    private void ensureUnique(UUID patientId, String label) {
        if (repository.existsByPatientIdAndDeletedAtIsNullAndLabelIgnoreCase(patientId, label)) {
            throw PrescriptionException.profileLabelConflict();
        }
    }

    private static String normalize(String label) {
        if (label == null) throw PrescriptionException.invalidProfileLabel();
        String normalized = label.trim();
        if (normalized.isEmpty() || normalized.length() > 60) throw PrescriptionException.invalidProfileLabel();
        return normalized;
    }
}
