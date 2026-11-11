-- DÉMO UNIQUEMENT (hors migration Flyway) : met 5 pharmacies proches du centre
-- de Marrakech de garde pour les 7 prochains jours. Rejouable sans danger.
INSERT INTO pharmacy_duties (pharmacy_id, starts_on, ends_on)
SELECT id,
       (now() AT TIME ZONE 'Africa/Casablanca')::date,
       (now() AT TIME ZONE 'Africa/Casablanca')::date + 6
FROM pharmacies
WHERE status = 'APPROVED' OR (source = 'OSM' AND status = 'UNVERIFIED')
ORDER BY ST_Distance(location, ST_SetSRID(ST_MakePoint(-7.9891, 31.6258), 4326)::geography)
LIMIT 5
ON CONFLICT DO NOTHING;
