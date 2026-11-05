package ma.ensa.pharma.prescription.storage;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * Image de l'ordonnance chiffrée en AES-256-GCM (RG06, ENF05 – loi 09-08) – table "prescription_image".
 * Supprimée à la demande du patient, au retrait ou à l'expiration de la demande.
 */
@Entity
@Table(name = "prescription_image")
public class PrescriptionImage {

    @Id
    @Column(name = "prescription_id")
    private UUID prescriptionId;

    /** Vecteur d'initialisation (12 octets aléatoires), différent à chaque chiffrement. */
    @Column(nullable = false)
    private byte[] iv;

    /** Image chiffrée (colonne bytea). */
    @Column(name = "encrypted_data", nullable = false)
    private byte[] encryptedData;

    protected PrescriptionImage() {
        // Constructeur vide exigé par JPA
    }

    public PrescriptionImage(UUID prescriptionId, byte[] iv, byte[] encryptedData) {
        this.prescriptionId = prescriptionId;
        this.iv = iv;
        this.encryptedData = encryptedData;
    }

    public UUID getPrescriptionId() { return prescriptionId; }
    public byte[] getIv() { return iv; }
    public byte[] getEncryptedData() { return encryptedData; }
}
