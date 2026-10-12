package ma.secdevops.medication.service;

import ma.secdevops.medication.dto.MedicationSearchResponse;
import ma.secdevops.medication.repository.MedicationSearchRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service métier pour la recherche de médicaments.
 */
@Service
public class MedicationSearchService {

    private final MedicationSearchRepository repository;

    public MedicationSearchService(MedicationSearchRepository repository) {
        this.repository = repository;
    }

    /**
     * Recherche les médicaments correspondant au mot-clé après nettoyage (trim).
     *
     * @param query texte de recherche
     * @param limit nombre maximum d'éléments retournés
     * @return liste des médicaments trouvés
     */
    public List<MedicationSearchResponse> search(String query, int limit) {
        String cleanQuery = (query != null) ? query.trim() : "";
        return repository.search(cleanQuery, limit);
    }
}
