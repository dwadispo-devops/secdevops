package ma.ensa.pharma.prescription.ai;

import ma.ensa.pharma.prescription.domain.VisionExtraction;

/**
 * Lecture d'une image d'ordonnance par un modèle vision.
 * L'interface permet de changer de fournisseur (Gemini, Ollama, OCR Tesseract en plan B)
 * et de simuler le modèle dans les tests.
 */
public interface VisionReader {

    /**
     * @param png image PNG déjà prétraitée
     * @return la sortie structurée du modèle (jamais null)
     * @throws ma.ensa.pharma.prescription.exception.PrescriptionException si le modèle est indisponible
     */
    VisionExtraction read(byte[] png);

    /** Nom du fournisseur actif, affiché dans la réponse pour la démo et le rapport de qualité. */
    String providerName();
}
