package ma.ensa.pharma.prescription.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Accès à la table "prescription" : save, findById, deleteById... sont fournis par Spring. */
public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {
    java.util.List<Prescription> findAllByPatientIdOrderByCreatedAtDesc(UUID patientId);
    java.util.List<Prescription> findAllByPatientIdAndFamilyProfileIsNullOrderByCreatedAtDesc(UUID patientId);
    java.util.List<Prescription> findAllByPatientIdAndFamilyProfileIdOrderByCreatedAtDesc(UUID patientId, UUID profileId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Prescription p where p.id=:id")
    java.util.Optional<Prescription> lockById(@org.springframework.data.repository.query.Param("id") UUID id);
}
