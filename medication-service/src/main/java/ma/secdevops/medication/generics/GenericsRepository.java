package ma.secdevops.medication.generics;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * US16 – Accès au catalogue pour la recherche de génériques.
 * Requêtes paramétrées uniquement ; la recherche par DCI utilise l'index idx_medication_dci1_lower (V1).
 */
@Repository
public class GenericsRepository {

    private static final String COLUMNS = """
            code, nom, dci1, dosage_brut, dosage1, unite_dosage1, forme, presentation,
            ppv, taux_remboursement, princeps_generique
            """;

    private static final RowMapper<CatalogRow> MAPPER = (rs, n) -> new CatalogRow(
            rs.getString("code"),
            rs.getString("nom"),
            rs.getString("dci1"),
            rs.getString("dosage_brut"),
            rs.getBigDecimal("dosage1"),
            rs.getString("unite_dosage1"),
            rs.getString("forme"),
            rs.getString("presentation"),
            rs.getBigDecimal("ppv"),
            rs.getObject("taux_remboursement", Integer.class),
            rs.getString("princeps_generique"));

    private final JdbcTemplate jdbc;

    public GenericsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<CatalogRow> findByCode(String code) {
        return jdbc.query("SELECT " + COLUMNS + " FROM medication WHERE code = ?", MAPPER, code)
                .stream().findFirst();
    }

    /** Tous les produits ayant la même DCI (comparaison insensible à la casse et aux espaces de bord). */
    public List<CatalogRow> findBySameDci(String dci) {
        return jdbc.query("SELECT " + COLUMNS + " FROM medication WHERE lower(dci1) = lower(?) ORDER BY code",
                MAPPER, dci.trim());
    }
}
