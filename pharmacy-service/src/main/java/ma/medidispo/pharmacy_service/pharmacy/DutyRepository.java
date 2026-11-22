package ma.medidispo.pharmacy_service.pharmacy;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DutyRepository extends Repository<Pharmacy, UUID> {

    interface DutyView {
        UUID getId();
        String getName();
        String getAddress();
        String getPhone();
        String getOpeningHours();
        Double getLatitude();
        Double getLongitude();
        String getDutyUntil();
    }

    @Query(value = """
        SELECT p.id AS id,
               p.name AS name,
               p.address AS address,
               p.phone AS phone,
               p.opening_hours AS "openingHours",
               p.latitude AS latitude,
               p.longitude AS longitude,
               CAST(MAX(d.ends_on) AS varchar) AS "dutyUntil"
        FROM pharmacies p
        JOIN pharmacy_duties d ON d.pharmacy_id = p.id
        WHERE CAST(:day AS date) BETWEEN d.starts_on AND d.ends_on
          AND (
              p.status = 'APPROVED'
              OR (p.source = 'OSM' AND p.status = 'UNVERIFIED')
          )
        GROUP BY p.id
        ORDER BY p.name
        """, nativeQuery = true)
    List<DutyView> findOnDuty(@Param("day") LocalDate day);
}
