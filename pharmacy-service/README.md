# pharmacy-service

Pharmacies et recherche par rayon (US04 / EF15). Spring Boot 4, PostgreSQL 16 + PostGIS 3.5, Flyway.

## Lancer en local

1. Copier `.env.example` en `.env` et choisir un mot de passe.
2. Démarrer la base (port 5433) :
   `docker run -d --name pharmacy-db -e POSTGRES_DB=pharmacydb -e POSTGRES_USER=pharma -e POSTGRES_PASSWORD=<mot de passe> -p 127.0.0.1:5433:5432 -v pharmacy-db-data:/var/lib/postgresql/data postgis/postgis:16-3.5`
3. `set -a; source .env; set +a; ./mvnw spring-boot:run` (port 8082).

## API

`GET /api/pharmacies/nearby?lat=&lng=&radius=2000&limit=50`
Pharmacies approuvées dans le rayon (100 à 20 000 m), triées par distance en mètres. Paramètres invalides : 400.

## Tests

`./mvnw test` : Testcontainers démarre un PostGIS jetable, aucune base locale requise (Docker doit tourner).

## Données

Les pharmacies importées d'OpenStreetMap (ODbL) ont `source = 'OSM'`. Stocks et horaires sont simulés.
