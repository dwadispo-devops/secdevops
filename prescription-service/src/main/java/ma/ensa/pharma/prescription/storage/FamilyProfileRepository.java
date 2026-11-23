package ma.ensa.pharma.prescription.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FamilyProfileRepository extends JpaRepository<FamilyProfile, UUID> {
    List<FamilyProfile> findAllByPatientIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID patientId);
    Optional<FamilyProfile> findByIdAndPatientIdAndDeletedAtIsNull(UUID id, UUID patientId);
    Optional<FamilyProfile> findByIdAndPatientId(UUID id, UUID patientId);
    boolean existsByPatientIdAndDeletedAtIsNullAndLabelIgnoreCase(UUID patientId, String label);
    boolean existsByPatientIdAndDeletedAtIsNullAndLabelIgnoreCaseAndIdNot(UUID patientId, String label, UUID excludedId);
}
