-- DÉMO UNIQUEMENT (hors migration Flyway) : pharmacies fictives autour de Marrakech.
INSERT INTO pharmacies (name, address, phone, opening_hours, latitude, longitude, status, source)
SELECT v.name, v.address, v.phone, v.opening_hours, v.lat, v.lng, 'APPROVED', 'REGISTERED'
FROM (VALUES
    ('Pharmacie Démo Jemaa el-Fna', 'Place Jemaa el-Fna, Marrakech', '0524000001', '08:00-22:00', 31.6258, -7.9891),
    ('Pharmacie Démo Guéliz',       'Avenue Mohammed V, Guéliz',     '0524000002', '08:30-21:00', 31.6355, -8.0100),
    ('Pharmacie Démo Hivernage',    'Boulevard de la Menara',        '0524000003', '09:00-20:00', 31.6200, -8.0110),
    ('Pharmacie Démo Menara',       'Avenue de la Menara',           '0524000004', '08:00-23:00', 31.6129, -8.0170),
    ('Pharmacie Démo Bab Doukkala', 'Rue Bab Doukkala, Médina',      '0524000005', '08:00-21:30', 31.6340, -7.9980),
    ('Pharmacie Démo Palmeraie',    'Route de la Palmeraie',         '0524000006', '09:00-19:00', 31.6700, -7.9600)
) AS v(name, address, phone, opening_hours, lat, lng)
WHERE NOT EXISTS (SELECT 1 FROM pharmacies p WHERE p.name = v.name);
