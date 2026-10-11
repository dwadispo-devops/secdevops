
## Démarrage rapide avec Docker

Prérequis : Docker avec le plugin Compose.

```bash
cp .env.example .env
# Ouvrir .env et remplacer les valeurs :
#   DB_PASSWORD : un mot de passe de votre choix
#   JWT_SECRET  : générer avec  openssl rand -base64 48
docker compose up -d --build
docker compose ps
```

- `auth-service` : http://localhost:8081 (base `auth-db` sur le port 5432)
- Arrêter : `docker compose down` (les données sont conservées dans un volume)
- Tout effacer, y compris les données : `docker compose down -v`
- Le fichier `.env` n'est jamais versionné ; seul `.env.example` l'est.
