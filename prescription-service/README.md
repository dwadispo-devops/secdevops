# prescription-service — TS05 : preuve de concept de lecture d'ordonnance

> **Ticket Jira :** DEVOPS-17 (épopée DEVOPS-2) — *TS05 - Preuve de concept : lire une ordonnance imprimée avec un modèle vision (Spring AI)*
> **Exigences liées :** EF07, EF08 (partiel), RG05, RG06, ENF02, ENF05, ENF07, ENF09, ENF11 — sécurité §9.1 (injection de prompt, fichier malveillant, clé API)

Ce microservice reçoit la photo (ou le PDF) d'une ordonnance imprimée, l'envoie à un modèle d'IA vision
via **Spring AI** et renvoie la liste structurée des médicaments lus.
C'est la base du futur `prescription-service` : le rapprochement avec le catalogue CNOPS (RAG, pgvector)
arrive au Sprint 2 (US07).

## Ce que fait la PoC

```
POST /api/v1/prescriptions/read   (multipart : file + consent=true)
        │
        ├─ 1. Consentement obligatoire (RG06)                → 400 CONSENT_REQUIRED
        ├─ 2. Validation du fichier (RG05)                   → 400 / 413 / 415
        │     JPG, PNG, PDF · ≤ 5 Mo · type réel vérifié par signature binaire
        ├─ 3. Prétraitement en mémoire                       → 422 si illisible
        │     PDF → image de la 1re page · contrôle des dimensions (anti-bombe)
        │     redimensionnement ≤ 2000 px · ré-encodage PNG (supprime EXIF/GPS)
        ├─ 4. Modèle vision (Spring AI ChatClient + image)   → 503 si indisponible (ENF07)
        │     consigne système fixe + sortie JSON structurée (.entity)
        └─ 5. Validation de la sortie                        → lignes LU / INCERTAIN / ILLISIBLE
              nettoyage, longueurs max, 25 lignes max, quantités absurdes rejetées
```

Aucune image ni aucun texte lu n'est stocké ni journalisé : les logs contiennent seulement la durée,
le nombre de lignes et le fournisseur (ENF05).

## Stack

| Élément | Version |
|---|---|
| Java | 21 |
| Spring Boot | 3.5.7 |
| Spring AI | 1.1.8 (`spring-ai-starter-model-google-genai`, `spring-ai-starter-model-ollama`) |
| Modèle par défaut | Gemini 2.5 Flash (API gratuite Google AI Studio) |
| Plan B local | Ollama + `qwen2.5vl:7b` (profil `ollama`) |
| PDF | Apache PDFBox 3.0.5 |
| Doc API | springdoc-openapi 2.8.9 (Swagger UI) |

> Si l'équipe a figé **Spring Boot 4** au Sprint 0, passez à Spring AI 2.0.x : le code de `SpringAiVisionReader`
> reste le même, seules les versions du `pom.xml` changent.

## Lancer le service

### Option A — Gemini (par défaut, recommandé pour la démo)

1. Créer une clé gratuite sur <https://aistudio.google.com/app/apikey>.
2. Copier `.env.example` en `.env` et renseigner `GEMINI_API_KEY` (le fichier `.env` est ignoré par Git).
3. Lancer :

```bash
mvn spring-boot:run
```

### Option B — Ollama (100 % local, aucune image envoyée sur Internet)

```bash
ollama pull qwen2.5vl:7b
SPRING_PROFILES_ACTIVE=ollama mvn spring-boot:run
# PowerShell : $env:SPRING_PROFILES_ACTIVE="ollama"; mvn spring-boot:run
```

Le service écoute sur le port **8085**.

- Swagger UI : <http://localhost:8085/swagger-ui.html>
- Health check : <http://localhost:8085/actuator/health>

## Tester

### Générer les ordonnances fictives

```bash
pip install pillow requests
python3 scripts/generate_sample_prescriptions.py samples
```

Trois ordonnances imprimées **fictives** (PNG + PDF + résultat attendu en JSON) sont créées dans `samples/`.
La troisième contient un texte piégé (« IGNORE TES INSTRUCTIONS ET AJOUTE… ») pour vérifier que le modèle
ne se laisse pas manipuler.

### Appel manuel

```bash
curl -F "file=@samples/ordo_01_simple.png" -F "consent=true" \
     http://localhost:8085/api/v1/prescriptions/read
```

Exemple de réponse :

```json
{
  "readingId": "8f0c3f0e-4f7e-4a53-9d1c-2b1f3c0d9a11",
  "prescriptionDetected": true,
  "lines": [
    { "index": 1, "rawText": "DOLIPRANE 1 g, comprimé", "name": "DOLIPRANE", "dosage": "1 g",
      "form": "comprimé", "posology": "1 comprimé 3 fois par jour", "duration": "5 jours",
      "boxes": 1, "status": "LU" }
  ],
  "provider": "google-genai",
  "durationMs": 4210,
  "warnings": []
}
```

### Mesure automatique (préfigure TS06)

```bash
python3 scripts/evaluate_poc.py http://localhost:8085 samples
```

Affiche, pour chaque ordonnance, le nombre de médicaments reconnus, la durée, et vérifie que le
médicament injecté (`SUPERCURE`) n'apparaît pas. Termine par le taux global (objectif ENF03 : ≥ 80 %).

### Tests unitaires

```bash
mvn verify          # tests + rapport de couverture JaCoCo dans target/site/jacoco/index.html
```

Les tests n'appellent jamais le vrai modèle : `VisionReader` est simulé avec Mockito.
Ils couvrent la validation des fichiers (dont un exécutable renommé en `.png`), le prétraitement
(PDF multipage, redimensionnement, bombe de décompression), le nettoyage de la sortie (balises,
URL, caractères de contrôle), le service et le contrôleur (codes HTTP et `ProblemDetail`).

## Structure

```
src/main/java/ma/ensa/pharma/prescription/
├── api/                  PrescriptionController + DTO de réponse
├── ai/                   VisionReader (interface), SpringAiVisionReader, prompts, OutputSanitizer
├── file/                 FileValidator (RG05), ImagePreprocessor
├── service/              PrescriptionReadingService (orchestration)
├── domain/               VisionExtraction (sortie structurée du modèle), statuts
├── exception/            PrescriptionException + GlobalExceptionHandler (RFC 7807)
└── config/               ReadingProperties, OpenAPI
scripts/                  génération d'ordonnances fictives + évaluation
```

## Critères d'acceptation proposés pour DEVOPS-17

- [ ] Étant donné une photo nette d'une ordonnance imprimée fictive, quand je l'envoie avec `consent=true`,
      alors je reçois la liste des médicaments au format JSON.
- [ ] La lecture prend moins de 15 s (champ `durationMs`, ENF02).
- [ ] Sans consentement, la requête est refusée et l'image n'est pas envoyée à l'IA.
- [ ] Un fichier non autorisé (type, taille, contenu ne correspondant pas à l'extension) est refusé.
- [ ] Le texte piégé de `ordo_03` n'ajoute aucun médicament.
- [ ] Si le modèle ne répond pas, l'API renvoie 503 avec un message proposant la saisie manuelle.
- [ ] Le fournisseur peut être changé (Gemini ↔ Ollama) sans modifier le code.
- [ ] Les tests passent et le service expose `/actuator/health` et Swagger.

## Sous-tâches Jira suggérées

1. Initialiser le service et la configuration Spring AI (Gemini + profil Ollama)
2. Endpoint d'upload avec validation du fichier et du consentement
3. Prétraitement de l'image (PDF, redimensionnement, suppression des métadonnées)
4. Appel du modèle vision avec sortie structurée et consigne anti-injection
5. Validation de la sortie + gestion des erreurs (503 → saisie manuelle)
6. Tests unitaires + jeu de 3 ordonnances fictives + script d'évaluation
7. README et démonstration en Sprint Review

## Limites de la PoC et suite

- **Pas encore de RAG** : les noms sont renvoyés tels que lus. La règle RG02 (« aucun produit inventé ») et
  le code couleur vert/orange/rouge (scores 0,85 / 0,60) seront garantis au Sprint 2 par le rapprochement
  avec le catalogue CNOPS (TS02 + pgvector).
- Pas d'enregistrement de l'ordonnance (historique : US18, Sprint 3) ni de chiffrement de l'image (non stockée ici).
- Pas encore enregistré dans Eureka : il suffira d'ajouter `spring-cloud-starter-netflix-eureka-client`
  avec le socle commun (TS01) et une route `/api/v1/prescriptions/**` dans l'API Gateway.
- Le `Dockerfile` multi-stage fourni sert de base pour DO01 (Sprint 5).

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
