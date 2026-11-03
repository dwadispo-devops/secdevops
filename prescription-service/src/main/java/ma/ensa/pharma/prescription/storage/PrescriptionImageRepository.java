package ma.ensa.pharma.prescription.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Accès à la table "prescription_image". */
public interface PrescriptionImageRepository extends JpaRepository<PrescriptionImage, UUID> {
}
