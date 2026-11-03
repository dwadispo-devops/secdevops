package ma.ensa.pharma.prescription.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Accès à la table "prescription" : save, findById, deleteById... sont fournis par Spring. */
public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {
}
