package ma.secdevops.medication.repository;

import ma.secdevops.medication.dto.MedicationSearchResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Référentiel d'accès aux données pour la recherche de médicaments.
 * Utilise JdbcTemplate avec requêtes SQL paramétrées et indexation trigramme PostgreSQL (pg_trgm).
 */
@Repository
public class MedicationSearchRepository {

    private static final String SEARCH_SQL = """
            SELECT
                code, nom, dci1, dosage1, unite_dosage1, forme, presentation,
                ppv, taux_remboursement, princeps_generique, dosage_brut,
                CASE
                    WHEN nom ILIKE ? THEN 1
                    WHEN nom ILIKE ? THEN 2
                    ELSE 3
                END AS pertinence_rank,
                similarity(nom, ?) AS sim
            FROM medication
            WHERE nom ILIKE ? OR nom % ?
            ORDER BY
                pertinence_rank ASC,
                sim DESC,
                nom ASC
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<MedicationSearchResponse> rowMapper = (rs, rowNum) -> {
        Integer taux = rs.getObject("taux_remboursement") != null ? rs.getInt("taux_remboursement") : null;
        return new MedicationSearchResponse(
                rs.getString("code"),
                rs.getString("nom"),
                rs.getString("dci1"),
                rs.getBigDecimal("dosage1"),
                rs.getString("unite_dosage1"),
                rs.getString("forme"),
                rs.getString("presentation"),
                rs.getBigDecimal("ppv"),
                taux,
                rs.getString("princeps_generique"),
                rs.getString("dosage_brut")
        );
    };

    public MedicationSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Recherche des médicaments par nom avec classement par pertinence.
     *
     * @param query texte de recherche saisi (déjà validé et trimmé)
     * @param limit nombre maximum de résultats à retourner
     * @return liste des résultats classés
     */
    public List<MedicationSearchResponse> search(String query, int limit) {
        String escapedQuery = escapeLike(query);
        String prefixParam = escapedQuery + "%";
        String containsParam = "%" + escapedQuery + "%";

        return jdbcTemplate.query(
                SEARCH_SQL,
                rowMapper,
                prefixParam,     // 1. CASE WHEN nom ILIKE ? (préfixe)
                containsParam,   // 2. CASE WHEN nom ILIKE ? (contient)
                query,           // 3. similarity(nom, ?)
                containsParam,   // 4. WHERE nom ILIKE ?
                query,           // 5. OR nom % ?
                limit            // 6. LIMIT ?
        );
    }

    /**
     * Échappe les caractères réservés de LIKE/ILIKE ('%', '_' et '\') pour éviter
     * tout détournement de pattern dans les clauses LIKE.
     */
    public static String escapeLike(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
    }
}
