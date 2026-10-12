package ma.secdevops.medication.importer;

import ma.secdevops.medication.model.Medication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;

/**
 * Accès aux données pour l'import des médicaments.
 * <p>
 * Utilise {@link JdbcTemplate#batchUpdate} pour des INSERT en lots.
 * La requête INSERT ... ON CONFLICT DO UPDATE garantit l'idempotence :
 * relancer l'import ne crée pas de doublons, il met à jour les données existantes.
 * </p>
 */
@Repository
public class MedicationRepository {

    private static final Logger log = LoggerFactory.getLogger(MedicationRepository.class);

    private static final String UPSERT_SQL = """
            INSERT INTO medication (
                code, nom, dci1, dosage1, unite_dosage1, forme, presentation,
                ppv, ph, prix_br, princeps_generique, taux_remboursement, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
            ON CONFLICT (code) DO UPDATE SET
                nom                 = EXCLUDED.nom,
                dci1                = EXCLUDED.dci1,
                dosage1             = EXCLUDED.dosage1,
                unite_dosage1       = EXCLUDED.unite_dosage1,
                forme               = EXCLUDED.forme,
                presentation        = EXCLUDED.presentation,
                ppv                 = EXCLUDED.ppv,
                ph                  = EXCLUDED.ph,
                prix_br             = EXCLUDED.prix_br,
                princeps_generique  = EXCLUDED.princeps_generique,
                taux_remboursement  = EXCLUDED.taux_remboursement,
                updated_at          = NOW()
            """;

    private final JdbcTemplate jdbc;

    public MedicationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Importe une liste de médicaments par lots.
     *
     * @param medications liste de médicaments à insérer/mettre à jour
     * @param batchSize   taille de chaque lot (ex : 500)
     * @return nombre total de lignes affectées
     */
    public int upsertAll(List<Medication> medications, int batchSize) {
        int total = 0;
        int start = 0;

        while (start < medications.size()) {
            int end = Math.min(start + batchSize, medications.size());
            List<Medication> batch = medications.subList(start, end);

            int[][] results = jdbc.batchUpdate(UPSERT_SQL, batch, batch.size(),
                    (PreparedStatement ps, Medication m) -> {
                        ps.setString(1, m.code());
                        ps.setString(2, m.nom());
                        ps.setString(3, m.dci1());
                        // dosage1 : peut être null
                        if (m.dosage1() != null) {
                            ps.setBigDecimal(4, m.dosage1());
                        } else {
                            ps.setNull(4, java.sql.Types.NUMERIC);
                        }
                        ps.setString(5, m.uniteDosage1());
                        ps.setString(6, m.forme());
                        ps.setString(7, m.presentation());
                        // prix : peuvent être null
                        if (m.ppv() != null) {
                            ps.setBigDecimal(8, m.ppv());
                        } else {
                            ps.setNull(8, java.sql.Types.NUMERIC);
                        }
                        if (m.ph() != null) {
                            ps.setBigDecimal(9, m.ph());
                        } else {
                            ps.setNull(9, java.sql.Types.NUMERIC);
                        }
                        if (m.prixBr() != null) {
                            ps.setBigDecimal(10, m.prixBr());
                        } else {
                            ps.setNull(10, java.sql.Types.NUMERIC);
                        }
                        ps.setString(11, m.princepsGenerique());
                        // taux : peut être null
                        if (m.tauxRemboursement() != null) {
                            ps.setInt(12, m.tauxRemboursement());
                        } else {
                            ps.setNull(12, java.sql.Types.SMALLINT);
                        }
                    });

            // Compter les lignes affectées
            for (int[] batchResult : results) {
                for (int r : batchResult) {
                    if (r > 0) total++;
                }
            }

            log.info("Lot {}/{} importé ({} médicaments)",
                    end, medications.size(), batch.size());
            start = end;
        }

        return total;
    }

    /**
     * Retourne le nombre de médicaments actuellement en base.
     */
    public int count() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM medication", Integer.class);
        return count != null ? count : 0;
    }
}
