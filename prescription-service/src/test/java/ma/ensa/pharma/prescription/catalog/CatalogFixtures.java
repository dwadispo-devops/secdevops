package ma.ensa.pharma.prescription.catalog;

import ma.ensa.pharma.prescription.domain.ReadLine;

import java.math.BigDecimal;

/** Données de test : extraits fictifs du format du catalogue CNOPS. */
public final class CatalogFixtures {

    public static final CatalogMedication DOLIPRANE_1000_CP = med("6118000040001", "DOLIPRANE", "1000", "MG",
            "COMPRIME", "1 BOITE 8 COMPRIME");
    public static final CatalogMedication DOLIPRANE_1000_CP_16 = med("6118000040002", "DOLIPRANE", "1000", "MG",
            "COMPRIME", "1 BOITE 16 COMPRIME");
    public static final CatalogMedication DOLIPRANE_500_CP = med("6118000040003", "DOLIPRANE", "500", "MG",
            "COMPRIME", "1 BOITE 16 COMPRIME");
    public static final CatalogMedication DOLIPRANE_1000_SACHET = med("6118000040004", "DOLIPRANE", "1000", "MG",
            "POUDRE EN SACHET", "1 BOITE 8 SACHET");
    public static final CatalogMedication AUGMENTIN_1G_SACHET = med("6118000050001", "AUGMENTIN", "1", "G",
            "POUDRE EN SACHET", "1 BOITE 12 SACHET");
    public static final CatalogMedication AUGMENTIN_500_SACHET = med("6118000050002", "AUGMENTIN", "500", "MG",
            "POUDRE EN SACHET", "1 BOITE 12 SACHET");

    /** Cas réels du catalogue CNOPS : AUGMENTIN sans dosage, "SACHET" seulement dans la présentation. */
    public static final CatalogMedication AUGMENTIN_CP_NO_DOSE = med("6118000160212", "AUGMENTIN", null, null,
            "COMPRIME PELLICULE", "1 BOITE 12 COMPRIME");
    public static final CatalogMedication AUGMENTIN_SACHET_NO_DOSE = med("6118000160182", "AUGMENTIN", null, null,
            "POUDRE POUR SUSPENSION BUVABLE", "1 BOITE 16 SACHET");
    public static final CatalogMedication AUGMENTIN_SACHET_NO_DOSE_24 = med("6118000161042", "AUGMENTIN", null, null,
            "POUDRE POUR SUSPENSION BUVABLE", "1 BOITE 24 SACHET");

    private CatalogFixtures() {
    }

    public static CatalogMedication med(String code, String nom, String dosage, String unite,
                                        String forme, String presentation) {
        return new CatalogMedication(code, nom, null, dosage == null ? null : new BigDecimal(dosage),
                unite, forme, presentation, null);
    }

    public static ReadLine line(String name, String dosage, String form) {
        return new ReadLine(1, name, dosage, form, 1, false);
    }
}
