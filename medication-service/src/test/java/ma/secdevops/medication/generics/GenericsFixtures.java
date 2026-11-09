package ma.secdevops.medication.generics;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Lignes réelles du catalogue CNOPS 2014 (data.gov.ma) utilisées par les tests US16. */
final class GenericsFixtures {

    private GenericsFixtures() {
    }

    static final String AMOX_CLAV = "AMOXICILLINE / ACIDE CLAVULANIQUE";
    static final String PSB = "POUDRE POUR SUSPENSION BUVABLE";

    // Princeps de départ : 16 sachets à 168,20 → 10,51 par sachet
    static final CatalogRow AUGMENTIN_16 = combo("6118000160182", "AUGMENTIN", PSB, "1 BOITE 16 SACHET", "168.2", 70, "P");
    // Génériques moins chers
    static final CatalogRow ZAMOX_24 = combo("6118000041177", "ZAMOX", PSB, "1 BOITE 24 SACHET", "167", 70, "G");
    static final CatalogRow CURAM_16_NON_REMBOURSE = combo("6118000220459", "CURAM 1G/125MG", PSB, "1 BOITE 16 SACHET", "116", 0, "G");
    static final CatalogRow NEOCLAV_16 = combo("6118000310594", "NEOCLAV", PSB, "1 BOITE 16 SACHET", "116", 70, "G");
    // Faute de frappe de la source : "SUSPENTION"
    static final CatalogRow ACLAV_12_TYPO = combo("6118000091684", "ACLAV", "POUDRE POUR SUSPENTION BUVABLE", "1 BOITE 12 SACHET", "99", 70, "G");
    // Le même produit sous deux codes
    static final CatalogRow ZAMOX_16_A = combo("6118010119156", "ZAMOX", PSB, "1 BOITE 16 SACHET", "116", 70, "G");
    static final CatalogRow ZAMOX_16_B = combo("6118000041160", "ZAMOX", PSB, "1 BOITE 16 SACHET", "116", 70, "G");
    // À exclure
    static final CatalogRow CLAVULIN_PRINCEPS = combo("6118000160496", "CLAVULIN", PSB, "1 BOITE 12 SACHET", "120.4", 70, "P");
    static final CatalogRow MAXICLAV_AUTRE_DOSE = new CatalogRow("6118000280286", "MAXICLAV", AMOX_CLAV, "500 / 62.5", null,
            "MG / MG", PSB, "1 BOITE 16 SACHET", new BigDecimal("90"), 70, "G");
    static final CatalogRow CLAVAM_PLUS_CHER = combo("6118010023965", "CLAVAM", PSB, "1 BOITE 12 SACHET", "130", 70, "G");
    static final CatalogRow GENERIQUE_SANS_PRIX = combo("6118000000099", "SANS PRIX", PSB, "1 BOITE 16 SACHET", null, 70, "G");

    // Paracétamol 1 g : "1 G" côté princeps, "1000 MG" côté générique
    static final CatalogRow DOLIPRANE_EFF = simple("6118000040361", "DOLIPRANE", "PARACETAMOL", "1", "G",
            "COMPRIME EFFERVESCENT", "1 BOITE 8 COMPRIME", "15.8", "P");
    static final CatalogRow ANDOL_EFF = simple("6118000340164", "ANDOL", "PARACETAMOL", "1000", "MG",
            "COMPRIME EFFERVESCENT", "1 BOITE 8 COMPRIME EFFERVESCENT", "14.5", "G");
    static final CatalogRow PARACETAMOL_CP = simple("6118000000101", "PARA CP", "PARACETAMOL", "1", "G",
            "COMPRIME", "1 BOITE 8 COMPRIME", "5", "G");
    static final CatalogRow DOLIPRANE_SECABLE = simple("6118000040972", "DOLIPRANE", "PARACETAMOL", "1", "G",
            "COMPRIME SECABLE", "1 BOITE 10 COMPRIME", "14", "P");

    // Sirops : comparables seulement si le flacon est identique
    static final CatalogRow SIROP_REF = simple("6118000000201", "SIROP REF", "CETIRIZINE", "1", "MG",
            "SOLUTION BUVABLE", "1 FLACON 60 ML", "40", "P");
    static final CatalogRow SIROP_MEME_FLACON = simple("6118000000202", "SIROP GEN", "CETIRIZINE", "1", "MG",
            "SOLUTION BUVABLE", "1 FLACON 60 ML", "31.9", "G");
    static final CatalogRow SIROP_AUTRE_FLACON = simple("6118000000203", "SIROP GEN 150", "CETIRIZINE", "1", "MG",
            "SOLUTION BUVABLE", "1 FLACON 150 ML", "35", "G");

    static CatalogRow combo(String code, String nom, String forme, String presentation, String ppv, int taux, String pg) {
        return new CatalogRow(code, nom, AMOX_CLAV, "1 / 125", null, "G / MG", forme, presentation,
                ppv == null ? null : new BigDecimal(ppv), taux, pg);
    }

    static CatalogRow simple(String code, String nom, String dci, String dose, String unit, String forme,
                             String presentation, String ppv, String pg) {
        return new CatalogRow(code, nom, dci, dose, new BigDecimal(dose), unit, forme, presentation,
                new BigDecimal(ppv), 70, pg);
    }

    /** Faux repository en mémoire : pas de base de données pour les tests de règles. */
    static GenericsRepository repository(CatalogRow... rows) {
        List<CatalogRow> all = new ArrayList<>(List.of(rows));
        return new GenericsRepository(null) {
            @Override
            public Optional<CatalogRow> findByCode(String code) {
                return all.stream().filter(r -> r.code().equals(code)).findFirst();
            }

            @Override
            public List<CatalogRow> findBySameDci(String dci) {
                return all.stream().filter(r -> r.dci1() != null && r.dci1().equalsIgnoreCase(dci.trim())).toList();
            }
        };
    }
}
