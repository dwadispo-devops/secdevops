-- DÉMO UNIQUEMENT (hors migration Flyway) : met 5 pharmacies proches du centre
-- de Marrakech de garde (de la veille à +6 jours, en UTC).
-- Le Maroc est à l'heure GMT (UTC+0) depuis le 20/09/2026. On évite volontairement
-- 'Africa/Casablanca' ici : la base PostGIS peut embarquer d'anciennes règles (UTC+1).
INSERT INTO pharmacy_duties (pharmacy_id, starts_on, ends_on)
SELECT id,
       (now() AT TIME ZONE 'UTC')::date - 1,
       (now() AT TIME ZONE 'UTC')::date + 6
FROM pharmacies
WHERE status = 'APPROVED' OR (source = 'OSM' AND status = 'UNVERIFIED')
ORDER BY ST_Distance(location, ST_SetSRID(ST_MakePoint(-7.9891, 31.6258), 4326)::geography)
LIMIT 5
ON CONFLICT DO NOTHING;
