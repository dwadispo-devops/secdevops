# auth-service

Microservice d'authentification de DwaDispo : comptes, mots de passe et jetons JWT (US01, US02, US03).

- **Port** : `8081`
- **Base de données** : PostgreSQL `authdb` (service `auth-db` du docker-compose)
- **Enregistré dans Eureka** sous le nom `AUTH-SERVICE`, exposé via la gateway sur `/api/auth/**`

## Lancer

```bash
# avec toute la plateforme (depuis la racine du dépôt)
docker compose up -d --build auth-service

# seul, en local
./mvnw spring-boot:run
./mvnw clean verify        # tests
```

## API publique (`/api/auth/**`)

| Méthode | Route | Description |
|---|---|---|
| `POST` | `/api/auth/register` | crée un compte **patient** (US01) |
| `POST` | `/api/auth/register/pharmacien` | crée un compte **pharmacien** ; sa pharmacie reste « en attente » jusqu'à validation par un administrateur (US03, EF06) |
| `POST` | `/api/auth/login` | renvoie `accessToken` (JWT, 15 min) et `refreshToken` (US02) |
| `POST` | `/api/auth/refresh` | renouvelle le jeton d'accès |
| `POST` | `/api/auth/logout` | révoque le refresh token |

Règle du mot de passe : 8 à 72 caractères, au moins une lettre et un chiffre. Les mots de passe sont hachés avec **BCrypt** (ENF04).

Le JWT contient `sub` (identifiant de l'utilisateur), `email` et `role` (`PATIENT`, `PHARMACIEN` ou `ADMIN`). Les autres services le vérifient avec la même clé `JWT_SECRET`.

## API interne (non exposée par la gateway)

| Méthode | Route | Description |
|---|---|---|
| `GET` | `/internal/users/{id}/contact` | e-mail et prénom d'un utilisateur actif, pour l'envoi des notifications par pharmacy-service (US14) |

Protégée par l'en-tête `X-Internal-Token`, qui doit être égal à `INTERNAL_API_TOKEN` (comparaison en temps constant). Si aucun jeton n'est configuré, tous les appels sont refusés (`403`). Seul le strict nécessaire est renvoyé (loi 09-08).

## Configuration

| Variable | Rôle |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | connexion PostgreSQL |
| `JWT_SECRET` | clé de signature des JWT, **secret** (Base64) |
| `INTERNAL_API_TOKEN` | jeton des appels internes, **secret** |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | adresse d'Eureka |

Les secrets sont dans le fichier `.env` à la racine (jamais commité) et, pour la CI/CD, dans les secrets de GitHub Actions (SEC03).

## Tests

- `AuthServiceTest` : inscription patient et pharmacien, e-mail déjà utilisé
- `RegisterRequestValidationTest` : règles de validation
- `JwtServiceTest`, `TokenServiceTest`, `TokenConcurrencyIntegrationTest` : jetons et refresh token
- `InternalUserControllerTest` : jeton interne obligatoire, compte désactivé ou inconnu
