# prescription-service

US17 fournit une estimation indicative du coût des lignes confirmées, sans inventer une base de remboursement : [fonctionnement, tests et recette](../docs/US17_ESTIMATION_ORDONNANCE.md).

Lecture d'une ordonnance par IA vision (TS05, Sprint 1) puis **reconnaissance des médicaments dans le
catalogue CNOPS avec un niveau de confiance** (US07, Sprint 2).

> **Jira :** DEVOPS-17 (TS05) · US07 — *En tant que patient, je veux que les médicaments de l'ordonnance
> soient reconnus dans le catalogue (RAG) avec un niveau de confiance*
> **Exigences :** EF07, EF08, RG01, RG02, RG05, RG06, ENF02, ENF03, ENF05, ENF07, ENF09, ENF11

## Fonctionnement

```
POST /api/v1/prescriptions/read   (multipart : file + consent=true)
  1. Consentement obligatoire (RG06)                        → 400 CONSENT_REQUIRED
  2. Validation du fichier (RG05 : JPG/PNG/PDF, ≤ 5 Mo, type réel vérifié)
  3. Prétraitement en mémoire (PDF → image, redimensionnement, suppression EXIF)
  4. Gemini lit chaque ligne : nom, dosage, forme, quantité   → 503 si l'IA est indisponible
  5. Nettoyage de la sortie de l'IA (SEC07)
  6. US07 – Rapprochement avec le catalogue CNOPS :
       a. recherche des candidats dans medication-service   GET /api/medications/search?q=…
       b. score de confiance de chaque candidat (nom + dosage + forme)
       c. 3 meilleures suggestions, uniquement des produits du catalogue (RG02)
```

### Score de confiance (US07)

```
score = 0,55 × similarité du nom   (trigrammes, comme pg_trgm : "DOLIPRAN" ≈ "DOLIPRANE")
      + 0,25 × dosage identique     (après conversion : "1 g" = "1000 MG" ; 0,3 si inconnu)
      + 0,20 × forme identique      ("cp" = "comprimé" = "COMPRIME", cherchée dans la forme ET la
                                     présentation du catalogue ; 0,5 si inconnue)
```

| Score | Couleur (écran US08) | Exemple |
|---|---|---|
| ≥ 0,85 | 🟢 reconnu | « DOLIPRANE 1 g comprimé » → DOLIPRANE 1000 MG, COMPRIME |
| 0,60 – 0,85 | 🟠 à vérifier (3 suggestions) | mauvais dosage, mauvaise forme, dosage absent |
| < 0,60 | 🔴 non reconnu (saisie manuelle) | nom absent du catalogue, ligne illisible |

Règles supplémentaires :
- **Ambiguïté** : si deux médicaments *différents* (dosage ou forme) ont presque le même score, la ligne
  est plafonnée à 0,84 (orange) : le patient choisit. Deux tailles de boîte du même médicament ne sont pas ambiguës.
- **Doute de l'IA** (`uncertain`) : la ligne n'est jamais verte.
- **Dosage impossible à vérifier** (absent de l'ordonnance ou du catalogue, ex. AUGMENTIN dans CNOPS) :
  la ligne reste orange, le patient confirme.
- **Catalogue indisponible** : la lecture fonctionne quand même ; les lignes sont rouges avec un
  avertissement (saisie manuelle, ENF07).

### Réponse

Format aligné sur l'interface `LigneOrdonnance` de l'écran de vérification (US08), complété par `quantite`
(consigne du prof : nom du médicament + quantité) :

```json
{
  "readingId": "8f0c3f0e-4f7e-4a53-9d1c-2b1f3c0d9a11",
  "prescriptionDetected": true,
  "lignes": [
    {
      "id": 1,
      "texteLu": "DOLIPRANE 1 g",
      "quantite": 1,
      "score": 0.94,
      "suggestions": [
        { "id": "6118000040001", "nom": "DOLIPRANE 1000 MG, COMPRIME, 1 BOITE 8 COMPRIME" }
      ]
    }
  ],
  "warnings": [],
  "provider": "google-genai",
  "durationMs": 5210
}
```

`suggestions[].id` est le **code CNOPS** : il permet aux autres services de retrouver le prix, les génériques
et la disponibilité en pharmacie.

## Lancer

### 1. Le catalogue (medication-service)

Voir `medication-service/README.md` : PostgreSQL via `infra/`, import CNOPS (profil `import`), puis
`mvn spring-boot:run` (port **8083**).

### 2. prescription-service

Créer `.env` (jamais commité) à partir de `.env.example` :

```properties
GEMINI_API_KEY=...
GEMINI_MODEL=gemini-3.5-flash
MEDICATION_SERVICE_URL=http://localhost:8083
# + les variables de l'US06 (base prescriptiondb, JWT_SECRET, PRESCRIPTION_IMAGE_KEY) : voir plus bas
```

```bash
mvn spring-boot:run          # port 8085
```

- Swagger : <http://localhost:8085/swagger-ui.html>
- Santé : <http://localhost:8085/actuator/health>
- Plan B local : `SPRING_PROFILES_ACTIVE=ollama` (modèle `qwen2.5vl:7b`)

## Tester

```bash
mvn verify                                  # tests + couverture JaCoCo (target/site/jacoco)
python3 scripts/generate_sample_prescriptions.py samples
python3 scripts/evaluate_poc.py http://localhost:8085 samples
```

Les tests n'appellent ni Gemini ni le vrai catalogue : le modèle est simulé (Mockito) et medication-service
est remplacé par un faux serveur (`MockRestServiceServer`) qui renvoie le format réel de son API.
Ils couvrent notamment : le calcul du score (dosages, formes, fautes de lecture), le choix des 3 suggestions,
les cas ambigus, les lignes illisibles, la panne du catalogue et le fait qu'aucun produit inventé n'est proposé.

Le script d'évaluation affiche, pour chaque ordonnance fictive, le texte lu, la couleur et le score de chaque
ligne, puis le taux de reconnaissance global (objectif ENF03 : ≥ 80 %).

## Structure

```
src/main/java/ma/ensa/pharma/prescription/
├── api/          PrescriptionController + DTO (LigneOrdonnanceDto, ProduitDto)
├── ai/           Lecture Gemini (Spring AI), consignes, nettoyage de la sortie
├── catalog/      US07 : MedicationCatalogClient, MatchScorer (score), CatalogMatcher (top 3)
├── file/         Validation et prétraitement des fichiers
├── service/      PrescriptionReadingService (orchestration)
├── domain/       VisionExtraction (sortie de l'IA), ReadLine (ligne nettoyée)
├── exception/    Erreurs fonctionnelles (RFC 7807)
└── config/       ReadingProperties, CatalogProperties, OpenAPI
```

## Choix d'architecture (US07)

Le cahier des charges cite *Spring AI VectorStore PGVector*. Le rapprochement réutilise plutôt
l'API de recherche de **medication-service** (pg_trgm), pour trois raisons :

1. **Une seule source du catalogue** : il appartient à medication-service, pas de copie à maintenir.
2. **Plus fiable sur des noms de marque** : une similarité de caractères compare mieux « DOLIPRAN » et
   « DOLIPRANE » qu'une similarité sémantique (embeddings).
3. **RG02 garanti** : seuls des produits renvoyés par le catalogue sont proposés.

Le principe du RAG est respecté : la sortie de l'IA est **ancrée** sur une base de référence (*retrieval*)
avant d'être présentée au patient. Choix à valider avec l'équipe et à reporter dans le dossier de conception.

## Résultat du test avec le vrai catalogue CNOPS

| Ligne lue | Score | 1ʳᵉ suggestion |
|---|---|---|
| DOLIPRANE 1 g, comprimé | 1,0 🟢 | DOLIPRANE 1 G, COMPRIME SECABLE |
| SPASFON 80 mg, comprimé | 1,0 🟢 | SPASFON 80 MG, COMPRIME ENROBE |
| AUGMENTIN 1 g/125 mg, sachet | 0,83 🟠 | AUGMENTIN, POUDRE POUR SUSPENSION BUVABLE, 1 BOITE 16 SACHET |

Le premier essai proposait un AUGMENTIN *comprimé* en tête : dans CNOPS, « SACHET » n'apparaît que dans la
présentation et le dosage est absent. Correction : la forme est aussi cherchée dans la présentation, et un
dosage invérifiable empêche le vert.

## Limites et suite

- L'écran de vérification (US08) doit ajouter le champ `quantite` à son interface (déjà envoyé par ce service).
- Bascule automatique Gemini → Ollama et OCR Tesseract : tickets à créer.

---

# US06 — Envoi de l'ordonnance (DEVOPS-18)

## À quoi sert ce service ?

`prescription-service` gère les **ordonnances** des patients.

- **TS05 (Sprint 1)** : lecture de l'ordonnance par l'IA (`/api/v1/prescriptions/read`).
- **US06 (Sprint 2)** : le patient **photographie ou importe** son ordonnance depuis la page `/scan`.
  Le service vérifie que c'est bien un patient connecté, qu'il a donné son **consentement**,
  que le fichier est correct (JPG, PNG ou PDF, 5 Mo max), puis il **enregistre l'ordonnance**
  et son **image chiffrée** dans la base de données.

## Ce qui a été ajouté avec l'US06

- Une base PostgreSQL `prescriptiondb` avec 2 tables : `prescription` et `prescription_image` (créées par Flyway).
- La vérification du **JWT** (seul un `PATIENT` peut envoyer une ordonnance).
- Le **chiffrement** de l'image (AES-256), car c'est une donnée de santé.
- 3 routes :
  - `POST /api/v1/prescriptions` → envoyer l'ordonnance
  - `GET /api/v1/prescriptions/{id}/image` → revoir l'image
  - `DELETE /api/v1/prescriptions/{id}/image` → supprimer l'image
- Le service dans `docker-compose.yaml` et une route dans l'API Gateway.
- La page Angular `/scan`.

## Comment démarrer le service

### 1. Préparer le fichier `.env` (une seule fois)

Dans le fichier `.env` à la **racine du projet**, il faut ajouter la clé de chiffrement des images :

```bash
echo "PRESCRIPTION_IMAGE_KEY=$(openssl rand -base64 32)" >> .env
```

> ⚠️ Cette clé ne doit pas être partagée ni committée, et il ne faut plus la changer
> (sinon les images déjà enregistrées ne peuvent plus être lues).

### 2. Lancer avec Docker Compose

Depuis la racine du projet :

```bash
docker compose up -d --build prescription-db prescription-service api-gateway auth-service
```

### 3. Vérifier que ça marche

```bash
docker compose ps
curl http://localhost:8085/actuator/health
```

Résultat attendu : `{"status":"UP"}`.

- Swagger : http://localhost:8085/swagger-ui.html
- Par la Gateway : http://localhost:8080/api/v1/prescriptions

> La clé Gemini (`GEMINI_API_KEY`) n'est pas obligatoire pour l'US06.
> Sans elle, le service démarre quand même (seule la lecture IA `/read` est indisponible).

### 4. Lancer les tests

```bash
cd prescription-service
mvn verify
```

### 5. Tester la page `/scan`

```bash
cd frontend
npm start
```

Puis ouvrir http://localhost:4200/scan.

> La page de connexion n'existe pas encore : pour tester, il faut mettre un jeton de patient
> dans le navigateur (F12 → Console) :
> `sessionStorage.setItem('access_token', '<jeton>')`

---

# US18 — Historique et relance des ordonnances (DEVOPS-30)

Un patient connecté retrouve ses ordonnances envoyées, de la plus récente à la plus ancienne, et peut relancer la lecture de l'une d'elles sans la rescanner.

## Ce qui a été ajouté

- `GET /api/v1/prescriptions` : liste des ordonnances du patient connecté (`id`, `status`, `fileType`, `createdAt`, `imageAvailable`). Jeton patient obligatoire, réponse jamais mise en cache, sans `patientId` ni données de lecture.
- Page Angular `/mes-ordonnances` : liste et bouton de relance.
- Relance : appel de `POST /api/v1/prescriptions/{id}/read?consent=true`, puis redirection vers `/verification` avec les médicaments lus.

## Tester chez soi

Depuis la racine du projet.

### 1. Prérequis

```bash
docker compose up -d --build
docker compose ps
```

La relance utilise la lecture IA : `GEMINI_API_KEY` doit être définie dans `.env`. Sans elle, l'historique fonctionne, mais la relance échoue (503 côté service).

### 2. Créer un compte patient et récupérer le jeton

Le mot de passe doit faire 8 à 72 caractères avec au moins une lettre et un chiffre.

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"patient.test@example.com","password":"Test1234","firstName":"Test","lastName":"Patient"}'

TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"patient.test@example.com","password":"Test1234"}' \
  | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
echo "${TOKEN:0:20}..."
```

Si le compte existe déjà, l'inscription est refusée : passer directement à la connexion.

### 3. Envoyer une ordonnance de test

```bash
curl -s -X POST http://localhost:8080/api/v1/prescriptions \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@prescription-service/samples/ordo_01_simple.png" \
  -F "consent=true"
```

La réponse contient l'`id` de l'ordonnance (code 201).

### 4. Vérifier l'historique

```bash
curl -s http://localhost:8080/api/v1/prescriptions -H "Authorization: Bearer $TOKEN"
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/prescriptions
```

Résultat attendu : la liste JSON avec l'ordonnance envoyée, puis `401` pour l'appel sans jeton.

### 5. Tester l'interface

```bash
cd frontend
npm start
```

1. Ouvrir http://localhost:4200/connexion et se connecter avec le compte patient.
2. Ouvrir http://localhost:4200/mes-ordonnances : la liste s'affiche.
3. Cliquer sur le bouton de relance d'une ordonnance : la page arrive sur `/verification` avec les médicaments lus.

Cas d'erreur à connaître :
- Sans connexion, la page affiche « Connectez-vous avec votre compte patient pour consulter vos ordonnances. »
- Jeton expiré : « Session expirée. Reconnectez-vous. »
- Aucun médicament lu : « Aucun médicament n'a pu être relu. Réessayez ou scannez une nouvelle ordonnance. »

### 6. Tests automatiques

Le test `StoredPrescriptionControllerTest` vérifie le 200 sans cache, l'absence de champs sensibles et le 401 sans jeton :

```bash
cd prescription-service
mvn verify
```
```
