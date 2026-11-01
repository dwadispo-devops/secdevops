package ma.medidispo.pharmacy_service.pharmacy;

import java.util.UUID;

/**
 * Fiche publique d'une pharmacie.
 * Elle ne contient volontairement PAS le numéro de licence, l'identifiant OSM
 * ni la source : ce sont des informations internes.
 */
public record PharmacyPublic(UUID id, String name, String address, String phone,
                             String openingHours, double latitude, double longitude,
                             String status) {

    static PharmacyPublic from(Pharmacy p) {
        return new PharmacyPublic(p.getId(), p.getName(), p.getAddress(), p.getPhone(),
                p.getOpeningHours(), p.getLatitude(), p.getLongitude(), p.getStatus());
    }
}
