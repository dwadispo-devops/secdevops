package ma.medidispo.pharmacy_service.pharmacy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PharmacyRepository extends JpaRepository<Pharmacy, UUID> {

    interface NearbyView {
        UUID getId();
        String getName();
        String getAddress();
        String getPhone();
        String getOpeningHours();
        Double getLatitude();
        Double getLongitude();
        Integer getDistanceM();
    }

    boolean existsByOsmId(Long osmId);

    @Query(value = """
        SELECT p.id AS id,
               p.name AS name,
               p.address AS address,
               p.phone AS phone,
               p.opening_hours AS "openingHours",
               p.latitude AS latitude,
               p.longitude AS longitude,
               CAST(ROUND(ST_Distance(
                   p.location,
                   CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography)
               )) AS integer) AS "distanceM"
        FROM pharmacies p
        WHERE (
            p.status = 'APPROVED'
            OR (p.source = 'OSM' AND p.status = 'UNVERIFIED')
        )
        AND ST_DWithin(
            p.location,
            CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography),
            :radius
        )
        ORDER BY ST_Distance(
            p.location,
            CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography)
        )
        LIMIT :limit
        """, nativeQuery = true)
    List<NearbyView> findNearby(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radius") double radius,
            @Param("limit") int limit
    );
}
