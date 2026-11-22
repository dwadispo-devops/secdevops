package ma.medidispo.pharmacy_service.pharmacy;

import java.util.List;

/** date = « aujourd'hui » calculé côté serveur à l'heure du Maroc (yyyy-MM-dd). */
public record DutyResponse(String date, List<DutyPharmacy> pharmacies) {}
