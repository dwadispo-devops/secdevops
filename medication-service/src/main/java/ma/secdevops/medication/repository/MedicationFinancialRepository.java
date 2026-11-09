package ma.secdevops.medication.repository;

import ma.secdevops.medication.dto.MedicationFinancialResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Collections;
import java.util.List;

@Repository
public class MedicationFinancialRepository {
    private final JdbcTemplate jdbc;
    public MedicationFinancialRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<MedicationFinancialResponse> findByCodes(List<String> codes) {
        if (codes.isEmpty()) return List.of();
        String placeholders = String.join(",", Collections.nCopies(codes.size(), "?"));
        return jdbc.query("SELECT code, nom, presentation, ppv, taux_remboursement FROM medication WHERE code IN ("
                + placeholders + ") ORDER BY code", (rs, n) -> new MedicationFinancialResponse(
                rs.getString("code"), rs.getString("nom"), rs.getString("presentation"),
                rs.getBigDecimal("ppv"), rs.getObject("taux_remboursement", Integer.class)), codes.toArray());
    }
}
