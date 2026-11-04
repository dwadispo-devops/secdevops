package ma.ensa.pharma.prescription.catalog;

import java.util.List;

/** Accès au catalogue des médicaments (medication-service). Interface simulée dans les tests. */
public interface CatalogClient {

    /**
     * @param query texte recherché (2 à 50 caractères)
     * @param limit nombre maximal de résultats
     * @return les médicaments du catalogue les plus proches (jamais null)
     * @throws CatalogUnavailableException si le catalogue ne répond pas
     */
    List<CatalogMedication> search(String query, int limit);
}
