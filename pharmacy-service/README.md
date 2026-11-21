# Pharmacy Service — DwaDispo

Service de gestion et de recherche de pharmacies.
Il couvre en particulier **US04 : « En tant que patient, je veux voir les pharmacies proches de moi sur une carte »**.

- Backend : `pharmacy-service/` (Spring Boot, Maven, PostgreSQL + PostGIS, Flyway, Eureka client)
- Frontend : `frontend/` (Angular, Leaflet), page `/pharmacies`
- Données : pharmacies importées depuis OpenStreetMap (GeoJSON)

> Les points marqués **(à vérifier)** n'ont pas été confirmés dans le code au moment de la rédaction. Les relire avant la fusion.

---

## 1. Vue d'ensemble

```
Navigateur (Angular + Leaflet)
        │  GET /api/pharmacies/nearby?lat=..&lng=..&radius=..&limit=..
        ▼
 Proxy de dev (proxy.conf.json)  ──►  pharmacy-service (port 8080)
                                              │
                                              ▼
                                   PostgreSQL + PostGIS
                                   (table pharmacies, migrations Flyway)
```

Fonctionnement de US04 :

1. Le patient ouvre `/pharmacies` : la carte s'affiche, centrée sur Marrakech.
2. Il clique sur **Me localiser** : le navigateur fournit sa position (Geolocation API).
3. Le front appelle `GET /api/pharmacies/nearby` avec cette position.
4. Le backend renvoie les pharmacies les plus proches, **triées par distance**, avec `distanceM` en mètres.
5. Le front affiche la liste et les marqueurs, et permet de chercher par nom.

---

## 2. API

### `GET /api/pharmacies/nearby`

| Paramètre | Type | Obligatoire | Règles |
|---|---|---|---|
| `lat` | nombre | oui | latitude, entre -90 et 90 |
| `lng` | nombre | oui | longitude, entre -180 et 180 |
| `radius` | entier (mètres) | non | de 1 à **20 000** (valeur par défaut : voir `PharmacyController`) |
| `limit` | entier | non | de 1 à **100**, **50 par défaut** |

Réponse `200` : tableau JSON trié par distance croissante (tableau vide `[]` si aucune pharmacie dans le rayon).

```json
[
  {
    "id": "67640efc-1528-44ad-a1ad-b77e00165a49",
    "name": "Pharmacie de la Place صيدلية الساحة",
    "address": "Marrakech",
    "phone": null,
    "openingHours": null,
    "latitude": 31.6254905,
    "longitude": -7.9885542,
    "distanceM": 62
  }
]
```

Remarques :

- `address`, `phone` et `openingHours` peuvent être `null` : les données OpenStreetMap sont incomplètes.
- Les noms peuvent contenir de l'arabe et du tifinagh : l'affichage doit rester en UTF-8.
- Paramètre manquant, non numérique ou hors bornes : réponse `400`.

### Autres endpoints du service (US03, écrits par un autre membre de l'équipe)

- `POST /api/pharmacies` : inscription d'une pharmacie (avec `license_number`)
- `GET /api/pharmacies/{id}` : détail d'une pharmacie

**(à vérifier)** : règles d'authentification (JWT) sur ces endpoints. Lors des tests de US04, `nearby` répondait sans jeton ; si une authentification est ajoutée, le front devra envoyer l'en-tête `Authorization`.

---

## 3. Prérequis

| Outil | Usage |
|---|---|
| JDK (version du `pom.xml`) | compiler et lancer le service |
| Docker | tests Testcontainers (PostGIS) et base locale |
| Node.js + npm | frontend Angular |
| Un navigateur Chrome ou Edge | tests de l'interface et simulation de position |

---

## 4. Lancer le projet en local

### 4.1 Base de données et backend

Démarrer PostgreSQL/PostGIS puis le service (les migrations Flyway s'appliquent au démarrage) :

```bash
cd pharmacy-service
./mvnw spring-boot:run
```

Vérification rapide :

```bash
curl -i "http://localhost:8080/api/pharmacies/nearby?lat=31.6258&lng=-7.9891&radius=5000"
```

Réponse attendue : `HTTP 200` et du JSON. Si la base est vide, la réponse est `[]` : passer à l'import (section 5).

Si le projet utilise Docker Compose (à vérifier), depuis la racine :

```bash
docker compose up --build -d
docker compose ps
```

### 4.2 Frontend

```bash
cd frontend
npm install
npx ng serve
```

Ouvrir `http://localhost:4200/pharmacies`.

Le fichier `frontend/proxy.conf.json` redirige `/api` vers `http://localhost:8080` en développement. Il est déclaré dans `angular.json` (`serve.options.proxyConfig`) : **aucune option supplémentaire n'est nécessaire**.

> Symptôme d'un proxy absent : erreur console `Unexpected token '<', "<!doctype"... is not valid JSON`. Angular reçoit alors sa propre page HTML au lieu de la réponse de l'API.
> Si `proxy.conf.json` est modifié, relancer `ng serve` : il n'est lu qu'au démarrage.

### 4.3 Développer dans une machine virtuelle

Dans une VM, le navigateur n'a ni GPS ni Wi-Fi réel : la position « Me localiser » est souvent fausse. Solutions :

- Simuler une position avec Chrome/Edge : `F12` → `Ctrl+Shift+P` → **Show Sensors** → *Geolocation* (ville prédéfinie ou *Custom location*).
- Ouvrir la page depuis le navigateur de la machine hôte via un tunnel SSH (la géolocalisation exige `localhost` ou HTTPS) :
  ```bash
  ssh -L 4200:localhost:4200 -L 8080:localhost:8080 utilisateur@IP_DE_LA_VM
  ```
  puis ouvrir `http://localhost:4200/pharmacies` sur l'hôte.

---

## 5. Importer les pharmacies OpenStreetMap

Le service contient un import au démarrage, **désactivé par défaut** (`OsmPharmacyImportRunner`).

### Format attendu

Un fichier **GeoJSON** avec un tableau `features`. Le service importe uniquement :

- les objets de type `Point` ;
- dont l'identifiant OSM est un nœud (`n` suivi de chiffres).

Les autres éléments (bâtiments, relations) sont comptés comme « ignorés ».

### Commande

Depuis `pharmacy-service/` :

```bash
./mvnw spring-boot:run \
  -Dspring-boot.run.arguments="--osm.import.enabled=true --osm.import.file=../data/pharmacies-marrakech.geojson"
```

Le service affiche à la fin :

```
========== IMPORT OSM TERMINÉ ==========
Pharmacies insérées : N
Pharmacies ignorées : M
========================================
```

Arrêter ensuite le service (`Ctrl+C`) et le relancer **sans** `--osm.import.enabled`.

Le paramètre `--osm.import.file` est obligatoire : sans lui, l'import s'arrête avec le message « Veuillez préciser le fichier avec --osm.import.file ».
L'import est transactionnel : en cas d'erreur, rien n'est inséré.

### Test de ré-import (anti-doublons)

Relancer l'import une seconde fois : le compteur « insérées » doit être **0** (déduplication par `osm_id`). **(à vérifier)**

### Générer le fichier GeoJSON (exemple à adapter)

Chaîne utilisée : extrait OSM du Maroc (Geofabrik) → découpe de Marrakech → filtre des pharmacies → export GeoJSON.

```bash
osmium tags-filter marrakech.osm.pbf nwr/amenity=pharmacy -o pharmacies-marrakech.osm.pbf
osmium export pharmacies-marrakech.osm.pbf -o pharmacies-marrakech.geojson
```

Les fichiers `.pbf` sont volumineux (le Maroc entier fait plus de 200 Mo) : ne pas les versionner. Le dossier `data/osm/` est dans le `.gitignore`.

### Licence

Données © OpenStreetMap contributors, licence ODbL. La carte affiche l'attribution ; la conserver.

---

## 6. Tests

### 6.1 Tests automatiques du backend

```bash
cd pharmacy-service
./mvnw test
```

Docker doit être démarré (Testcontainers lance une base PostGIS temporaire).
Résultat attendu : `BUILD SUCCESS` (21 tests lors de la dernière exécution).

| Classe de test | Ce qu'elle vérifie |
|---|---|
| `NearbyPharmaciesApiTest` | endpoint `nearby` : rayon, `limit`, paramètres invalides (`US04-AC3` : `limit=1` ne renvoie que la plus proche) |
| `PharmacyRepositoryTest` | requête géographique PostGIS et respect de la limite |

### 6.2 Tests de l'API avec curl

```bash
BASE="http://localhost:8080/api/pharmacies/nearby"

echo "=== Cas valides (attendu : 200) ==="
for q in "lat=31.6258&lng=-7.9891&radius=500" \
         "lat=31.6258&lng=-7.9891&radius=5000" \
         "lat=31.6258&lng=-7.9891&radius=5000&limit=1" \
         "lat=31.6258&lng=-7.9891&radius=20000&limit=100"; do
  echo -n "$q -> "
  curl -s -o /tmp/r.json -w "%{http_code} " "$BASE?$q"
  python3 -c "import json; print(len(json.load(open('/tmp/r.json'))), 'résultats')"
done

echo "=== Cas invalides (attendu : 400) ==="
for q in "lat=31.6&lng=-8&radius=20001" "lat=31.6&lng=-8&limit=0" \
         "lat=31.6&lng=-8&limit=101" "lng=-8" "lat=abc&lng=-8" \
         "lat=95&lng=-8" "lat=31.6&lng=200" \
         "lat=31.6&lng=-8&radius=0" "lat=31.6&lng=-8&radius=-5"; do
  echo -n "$q -> "
  curl -s -o /dev/null -w "%{http_code}\n" "$BASE?$q"
done
```

Résultats observés (base de Marrakech importée, position de l'ENSA) :

| Requête | Résultat |
|---|---|
| rayon 500 m / 1000 m / 5000 m | 11 / 28 / 50 pharmacies |
| `limit=1` | 1 pharmacie (la plus proche) |
| `radius=20000&limit=100` | 100 pharmacies |
| rayon > 20000, `limit` 0 ou > 100, paramètre manquant ou invalide, coordonnées impossibles, rayon nul ou négatif | `400` |
| `lat=0&lng=0&radius=5000` (aucune pharmacie) | `[]` avec `200` |

Contrôle du tri et du rayon :

```bash
curl -s "$BASE?lat=31.6258&lng=-7.9891&radius=1000" | python3 -c "
import sys, json
d = json.load(sys.stdin)
dist = [p['distanceM'] for p in d]
print('nombre :', len(d))
print('trié par distance :', dist == sorted(dist))
print('toutes <= 1000 m :', all(x <= 1000 for x in dist))
print('champs :', sorted(d[0].keys()))"
```

### 6.3 Tests manuels de l'interface

Page : `http://localhost:4200/pharmacies`

| # | Scénario | Résultat attendu |
|---|---|---|
| 1 | Ouverture de la page | carte centrée sur Marrakech, liste de pharmacies, compteur |
| 2 | **Me localiser**, position autorisée | point bleu, précision affichée, liste recalculée |
| 3 | **Me localiser**, position refusée | message « Autorisation de localisation refusée » |
| 4 | Position indisponible (Sensors : *Location unavailable*) | message d'erreur, la page ne plante pas |
| 5 | Clic sur une pharmacie de la liste | la carte zoome et ouvre sa bulle |
| 6 | Clic sur un marqueur vert | bulle avec le nom et la distance |
| 7 | Recherche par nom | liste et marqueurs filtrés, compteur mis à jour |
| 8 | Pharmacie sans adresse | « Adresse non renseignée » |
| 9 | Distances | en mètres sous 1000 m, sinon en km |
| 10 | Position simulée : Gueliz `31.6340, -8.0100` | liste recalculée autour de ce point |
| 11 | Position simulée sans pharmacie : `0, 0` | « Aucune pharmacie trouvée. », pas d'erreur console |
| 12 | Backend arrêté | message « Impossible de récupérer les pharmacies » et bouton **Réessayer** fonctionnel après redémarrage |
| 13 | Noms en arabe et tifinagh | affichés correctement dans la liste et les bulles |
| 14 | Écran étroit (`F12`, mode appareil) | liste et carte utilisables |

### 6.4 Tests de bout en bout (Docker, si utilisé)

```bash
docker compose up --build -d
docker compose ps
```

Rejouer ensuite les tests curl via le point d'entrée de la passerelle (gateway). **(à vérifier)** : port et préfixe de route.

---

## 7. Dépannage

| Symptôme | Cause probable | Solution |
|---|---|---|
| `Unexpected token '<'` dans la console | proxy Angular non chargé | vérifier `proxyConfig` dans `angular.json`, relancer `ng serve` |
| Liste vide, `[]` | base sans données | importer le GeoJSON (section 5) |
| Toujours 50 résultats | limite par défaut du backend | normal ; envoyer `limit` (max 100) pour changer |
| Point bleu loin de la vraie position | VM sans GPS ni Wi-Fi | simuler la position avec Sensors, ou utiliser le navigateur de l'hôte |
| Géolocalisation bloquée | page en HTTP hors `localhost` | utiliser `localhost` ou HTTPS |
| `./mvnw test` échoue au démarrage | Docker arrêté | démarrer Docker |
| Import : « Veuillez préciser le fichier » | option manquante | ajouter `--osm.import.file=...` |

---

## 8. Structure des fichiers

```
frontend/
  proxy.conf.json                      proxy de développement /api -> localhost:8080
  angular.json                         déclare le proxy (serve.options.proxyConfig)
  src/app/pages/pharmacies.ts          page : carte, liste, géolocalisation, recherche
  src/app/pages/pharmacies.scss        styles de la page
  src/app/services/pharmacy.service.ts appel de l'API nearby
pharmacy-service/src/main/java/ma/medidispo/pharmacy_service/
  pharmacy/PharmacyController.java     endpoints REST, validation des paramètres
  pharmacy/PharmacyRepository.java     requête géographique PostGIS
  pharmacy/Pharmacy.java               entité
  osm/OsmPharmacyImportRunner.java     import au démarrage (osm.import.enabled)
  osm/OsmPharmacyImportService.java    lecture du GeoJSON et insertion
data/osm/                              données locales, non versionnées
```

---

## 9. Limites connues et pistes d'amélioration

- Les données OpenStreetMap n'ont souvent ni adresse, ni téléphone, ni horaires.
- Pas de disponibilité en temps réel. Les gardes sont lues depuis `pharmacy_duties` (voir « Pharmacies de garde »); leur saisie n'est pas encore gérée.
- La précision de la position dépend de l'appareil ; envisager un clic sur la carte ou une recherche d'adresse comme alternative.
- Cercle de précision autour du point bleu et choix du rayon (1 km, 2 km, 5 km) côté interface.
- En production : servir le site en HTTPS et rediriger `/api` vers le service par le serveur web (le proxy Angular ne sert qu'en développement).

---

## 10. Workflow Git

- Branches de travail créées à partir de `develop`, nommées `DEVOPS-XX-description`.
- Merge requests vers `develop` ; `main` ne reçoit que `develop` à la fin du sprint.
- Ne pas pousser directement sur `main` ni sur `develop`.

## US09 : lancement local et demandes de disponibilite

# pharmacy-service

Pharmacies et recherche par rayon (US04 / EF15). Spring Boot 4, PostgreSQL 16 + PostGIS 3.5, Flyway.

## Lancer en local

1. Copier `.env.example` en `.env` et choisir un mot de passe.
2. Démarrer la base (port 5433) :
   `docker run -d --name pharmacy-db -e POSTGRES_DB=pharmacydb -e POSTGRES_USER=pharma -e POSTGRES_PASSWORD=<mot de passe> -p 127.0.0.1:5433:5432 -v pharmacy-db-data:/var/lib/postgresql/data postgis/postgis:16-3.5`
3. `set -a; source .env; set +a; ./mvnw spring-boot:run` (port 8082).

## API

### Annuler une réservation (US19 / EF22)

`POST /api/availability-requests/{request}/reservations/{id}/cancel` — rôle `PATIENT`, sans corps.
Le patient est identifié par le jeton. Une réponse `200` retourne la réservation avec `status: "ANNULEE"`.
Les réponses `401` et `403` indiquent respectivement un jeton absent/invalide/expiré et un rôle non autorisé.
La réponse `404` est uniforme pour une réservation inconnue, appartenant à un autre patient, ou dont la demande ne correspond pas.
La réponse `409` indique que la réservation du patient n'est plus annulable (déjà retirée, annulée ou expirée).
L'annulation libère le quota des réservations actives et permet de réserver à nouveau sur la même demande.
Elle ne purge pas l'image d'ordonnance et ne crée pas de notification.

### PowerShell

Depuis `pharmacy-service`, exécuter `./start-local.ps1` avec Java 21 et Docker actifs.
Le script charge explicitement le fichier `.env` local (Spring Boot ne le charge pas
automatiquement). Renseigner les quatre variables de `.env.example`, dont
`JWT_SECRET` en base64, et les identifiants réellement créés dans PostgreSQL.
Changer les variables `POSTGRES_*` d'un conteneur déjà initialisé ne recrée pas
ses rôles ni ses bases. Ne pas supprimer son volume pour corriger une connexion.
PostGIS doit être activé par un administrateur si le compte applicatif n'est pas
superutilisateur. Pour lancer un JAR déjà compilé :
`./start-local.ps1 -Jar target/pharmacy-service-0.0.1-SNAPSHOT.jar`.

`GET /api/pharmacies/nearby?lat=&lng=&radius=2000&limit=50`
Pharmacies approuvées dans le rayon (100 à 20 000 m), triées par distance en mètres. Paramètres invalides : 400.

## Tests

`./mvnw test` : Testcontainers démarre un PostGIS jetable, aucune base locale requise (Docker doit tourner).

## Données

Les pharmacies importées d'OpenStreetMap (ODbL) ont `source = 'OSM'`. Stocks et horaires sont simulés.
Le proxy Angular transmet /api/** a la passerelle sur le port 8080 ; celle-ci route les demandes de disponibilite vers pharmacy-service (8082).

## Notifications (US14 / EF24)

Les utilisateurs sont prévenus **dans le site** (cloche 🔔 du menu) et **par e-mail** des événements qui les concernent.

### Événements notifiés

| Événement | Destinataire | Type | Lien ouvert |
|---|---|---|---|
| Nouvelle demande de disponibilité | pharmaciens des pharmacies contactées | `NOUVELLE_DEMANDE` | `/pharmacien/demandes` |
| Réponse d'une pharmacie | patient | `REPONSE_PHARMACIE` | `/suivi-demande/{id}` |
| Réservation confirmée | patient | `RESERVATION_CONFIRMEE` | `/suivi-demande/{id}` |
| Nouvelle réservation | pharmacien | `NOUVELLE_RESERVATION` | `/pharmacien/demandes` |
| Retrait validé au comptoir | patient | `RETRAIT_CONFIRME` | `/suivi-demande/{id}` |
| Réservation expirée (2 h, US12) | patient | `RESERVATION_EXPIREE` | `/suivi-demande/{id}` |

La réservation et le QR patient US12, les contrôles de durée et la recette navigateur sont détaillés dans le [guide US12](../docs/US12_RESERVATION_QR.md).

Une pharmacie sans pharmacien inscrit (import OSM) est ignorée. Un pharmacien n'est prévenu qu'une fois par demande, même s'il possède plusieurs pharmacies contactées.

### Confidentialité (ENF05, loi 09-08)

- Les messages ne contiennent **jamais le nom des médicaments** : uniquement le nom de la pharmacie et le prix.
- Les journaux ne contiennent aucune donnée personnelle : seulement l'identifiant technique de la notification.
- pharmacy-service ne stocke pas les adresses e-mail : il les demande à auth-service au moment de l'envoi.

### API (gateway : `/api/notifications/**`, utilisateur connecté)

| Méthode | Route | Rôle | Réponse |
|---|---|---|---|
| `GET` | `/api/notifications/me` | patient ou pharmacien | `{ items: [...20 dernières], unread: n }` |
| `POST` | `/api/notifications/{id}/read` | propriétaire de la notification | `204`, ou `404` si elle appartient à un autre utilisateur |
| `POST` | `/api/notifications/read-all` | utilisateur connecté | `204` |

### Envoi des e-mails : boîte d'envoi (pattern *outbox*)

1. La notification est enregistrée **dans la même transaction** que l'événement (réponse, réservation…), avec `email_status = PENDING`.
2. La tâche `EmailOutbox` s'exécute toutes les 30 s : elle verrouille un lot de 20 notifications (`FOR UPDATE SKIP LOCKED`), récupère l'adresse auprès d'auth-service, puis envoie l'e-mail.
3. Statut final : `SENT`, `SKIPPED` (utilisateur inconnu ou désactivé), ou `FAILED` après 5 échecs. En cas d'échec, l'envoi est réessayé au passage suivant.

Le métier ne dépend donc jamais du serveur e-mail : si celui-ci est en panne, les réponses et réservations continuent de fonctionner et les e-mails partent plus tard.

### Configuration

| Variable | Rôle | Valeur par défaut |
|---|---|---|
| `NOTIFICATIONS_EMAIL_ENABLED` | active l'envoi des e-mails | `false` (désactivé dans les tests) |
| `MAIL_HOST` / `MAIL_PORT` | serveur SMTP | `localhost` / `1025` |
| `MAIL_FROM` | expéditeur | `no-reply@dwadispo.local` |
| `AUTH_SERVICE_URL` | adresse d'auth-service (appel interne) | `http://localhost:8081` |
| `INTERNAL_API_TOKEN` | jeton partagé avec auth-service, **secret** | *(vide : e-mails refusés)* |
| `FRONT_URL` | adresse du site, pour les liens des e-mails | `http://localhost:4200` |

En local, **Mailpit** (service `mailpit` du `docker-compose.yaml`) reçoit tous les e-mails sans rien envoyer pour de vrai. Interface : **http://localhost:8025**.

### Tests

- `NotificationIntegrationTest` : chaque événement crée la bonne notification ; un utilisateur ne voit et ne marque que les siennes ; 401 sans jeton.
- `EmailOutboxIntegrationTest` : envoi puis `SENT` ; jamais envoyé deux fois ; `SKIPPED` si l'utilisateur est inconnu ; réessai puis `FAILED` après 5 échecs.
- `EmailComposerTest` : contenu de l'e-mail ; un lien externe n'est jamais repris.

## Pharmacies de garde (US15)

`GET /api/pharmacies/garde` (public) renvoie les pharmacies de garde du jour.

- Paramètres optionnels `lat` et `lng`, à fournir ensemble (sinon 400). Avec la position, les résultats sont triés par distance (`distanceM`), sinon par nom.
- « Aujourd'hui » est calculé côté serveur à l'heure du Maroc (`Africa/Casablanca`).
- Les périodes de garde sont stockées dans `pharmacy_duties` (migration `V10__pharmacy_duties.sql`) : une ligne par pharmacie avec `starts_on` et `ends_on`, bornes incluses.
- Seules les pharmacies visibles dans `/nearby` sont renvoyées (`APPROVED`, ou `OSM` + `UNVERIFIED`).
- Données de démonstration (hors Flyway) : `scripts/demo-pharmacies.sql` puis `scripts/demo-gardes.sql`.


## Notifications asynchrones avec Kafka (US14, évolution)

Pour supporter de nombreux utilisateurs, les notifications ne sont plus créées pendant la requête de l'utilisateur.

**Fonctionnement**
1. Une action métier (nouvelle demande, réponse, réservation, retrait) publie un `NotificationEvent`.
2. Après la transaction (AFTER_COMMIT), l'événement est envoyé dans le topic Kafka `dwadispo.notifications` (3 partitions).
   La clé est l'identifiant de la demande ou de la réservation : les événements d'une même demande restent dans l'ordre.
3. Trois consommateurs (groupe `notifications`) lisent les partitions en parallèle et créent les notifications.
   L'e-mail est ensuite envoyé par l'outbox existante.

**Garanties**
- Idempotence : un événement déjà traité est ignoré (table `notification_events_traites`, migration V11).
- Secours : si Kafka est indisponible, l'événement est traité directement ; aucune notification n'est perdue.
- ENF05 : les événements ne contiennent que des identifiants, jamais de nom de médicament.

**Configuration**

| Variable | Rôle | Valeur par défaut |
|---|---|---|
| `NOTIFICATIONS_KAFKA_ENABLED` | active Kafka (sinon traitement direct, utilisé par les tests) | `false` |
| `KAFKA_BOOTSTRAP_SERVERS` | adresse de Kafka | `localhost:9092` |

**Observer Kafka**
```bash
# Interface web (outil de développement) : http://127.0.0.1:8090
docker compose --profile outils up -d kafka-ui

# État des consommateurs (le LAG doit être à 0)
docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --group notifications
```
