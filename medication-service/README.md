# medication-service

US17 ajoute une consultation financière par codes exacts : [contrats, tests et guide de recette](../docs/US17_ESTIMATION_ORDONNANCE.md).

Microservice de gestion du **référentiel des médicaments CNOPS** (data.gov.ma).

## Prérequis
- Java 21
- Maven 3.9+
- Docker Desktop (pour PostgreSQL et les tests Testcontainers)

---

## 1. Démarrer PostgreSQL avec Docker

```bash
# Depuis le dossier infra/
cd infra/

# Copier le template et remplir le mot de passe
cp .env.example .env
# Éditer .env et mettre un mot de passe pour POSTGRES_PASSWORD

# Démarrer le conteneur
docker compose up -d

# Vérifier que la base est prête
docker compose ps
# Le statut doit afficher "healthy"
```

---

## 2. Lancer l'import du référentiel CNOPS

L'import est **idempotent** : on peut le relancer autant de fois qu'on veut sans créer de doublons.

```bash
# Depuis le dossier medication-service/
cd medication-service/

# Compiler d'abord
mvn clean package -DskipTests

# Lancer l'import (profil "import" requis)
# Le mot de passe doit correspondre à celui dans infra/.env
DB_PASSWORD=<votre_mot_de_passe> mvn spring-boot:run \
    -Dspring-boot.run.profiles=import

# Sur Windows PowerShell :
$env:DB_PASSWORD="<votre_mot_de_passe>"
mvn spring-boot:run -Dspring-boot.run.profiles=import

# Ou via JAR :
DB_PASSWORD=<votre_mot_de_passe> java -jar target/medication-service-0.0.1-SNAPSHOT.jar \
    --spring.profiles.active=import
```

**Résultat attendu en sortie :**
```
RAPPORT D'IMPORT
  Lignes lues dans le fichier : 5917
  Lignes importées (upsert)   : 5917
  Lignes ignorées             : 0
```

---

## 3. Voir la base de données

### Via DBeaver ou pgAdmin

| Paramètre | Valeur |
|-----------|--------|
| Hôte | `localhost` |
| Port | `5432` |
| Base de données | `medication_db` |
| Utilisateur | `medication_user` |
| Mot de passe | *(voir `infra/.env`)* |

### Via psql en ligne de commande

```bash
# Se connecter au conteneur
docker exec -it medication_postgres psql -U medication_user -d medication_db

# Ou directement depuis votre machine (si psql installé)
psql -h localhost -p 5432 -U medication_user -d medication_db
```

---

## 4. Requêtes de vérification

```sql
-- Nombre total de médicaments importés
SELECT COUNT(*) FROM medication;

-- Médicaments avec DCI1 NULL
SELECT COUNT(*) FROM medication WHERE dci1 IS NULL;

-- Répartition princeps / générique
SELECT princeps_generique, COUNT(*) FROM medication GROUP BY princeps_generique;

-- Répartition des taux de remboursement
SELECT taux_remboursement, COUNT(*) FROM medication GROUP BY taux_remboursement;

-- 10 médicaments dont le nom contient 'DOLIPRANE' (recherche trigramme)
SELECT code, nom, ppv, taux_remboursement
FROM medication
WHERE nom % 'DOLIPRANE'
ORDER BY similarity(nom, 'DOLIPRANE') DESC
LIMIT 10;

-- Tous les génériques d'une même DCI (ex : OXALIPLATINE)
SELECT code, nom, dosage1, unite_dosage1, ppv, ph
FROM medication
WHERE lower(dci1) = 'oxaliplatine'
ORDER BY nom;

-- Prix minimum et maximum
SELECT MIN(ppv), MAX(ppv), MIN(ph), MAX(ph) FROM medication;
```

---

## 5. Recherche de Médicaments (API REST — US05)

Le microservice expose une API REST de recherche et d'autocomplétion sur le port **8083** (configurable via `SERVER_PORT`).

### Démarrer le serveur API

```powershell
# Définir le mot de passe de la base
$env:DB_PASSWORD="<votre_mot_de_passe>"

# Lancer le microservice en mode normal (serveur web actif sur le port 8083)
java -jar target/medication-service-0.0.1-SNAPSHOT.jar

# Ou avec Maven :
mvn spring-boot:run
```

### Endpoints disponibles

- **Recherche** : `GET /api/medications/search?q={texte}&limit={n}`
- **Swagger UI** : `http://localhost:8083/swagger-ui.html`
- **Documentation OpenAPI** : `http://localhost:8083/v3/api-docs`
- **Santé du service** : `http://localhost:8083/actuator/health`

### Paramètres de recherche

| Paramètre | Requis | Valeurs / Contraintes | Description |
|-----------|--------|-----------------------|-------------|
| `q` | **Oui** | Min 2, max 50 caractères (après trim) | Texte recherché (insensible à la casse) |
| `limit` | Non | Min 1, max 20 (**10 par défaut**) | Nombre maximum de résultats |

### Algorithme de pertinence

La recherche utilise l'extension PostgreSQL `pg_trgm` et l'index GIN sur `nom` pour trier les résultats selon 3 niveaux de pertinence :
1. **Préfixe** : Noms commençant par `q` (ex : `DOLIP...`)
2. **Sous-chaîne** : Noms contenant `q`
3. **Similarité trigramme** : Tolérance aux fautes de frappe légères (ex : `doliprne` $\rightarrow$ `DOLIPRANE`)

### Exemples d'appels

#### 1. Recherche par nom ("dolip")
```powershell
curl.exe -s "http://localhost:8083/api/medications/search?q=dolip&limit=2"
```

**Réponse (200 OK) :**
```json
[
  {
    "code": "6118000040279",
    "nom": "DOLIPRANE",
    "dci1": "PARACETAMOL",
    "dosage1": 300.000,
    "uniteDosage1": "MG",
    "forme": "POUDRE POUR SOLUTION BUVABLE",
    "presentation": "1 BOITE 12 SACHET",
    "ppv": 12.85,
    "tauxRemboursement": 70,
    "princepsGenerique": "P"
  },
  {
    "code": "6118000040293",
    "nom": "DOLIPRANE",
    "dci1": "PARACETAMOL",
    "dosage1": 150.000,
    "uniteDosage1": "MG",
    "forme": "SUPPOSITOIRE",
    "presentation": "1 BOITE 10 SUPPOSITOIRE",
    "ppv": 9.70,
    "tauxRemboursement": 70,
    "princepsGenerique": "P"
  }
]
```

#### 2. Tolérance aux fautes de frappe ("doliprne")
```powershell
curl.exe -s "http://localhost:8083/api/medications/search?q=doliprne&limit=1"
```

#### 3. Paramètre invalide (< 2 caractères)
```powershell
curl.exe -s "http://localhost:8083/api/medications/search?q=a"
```

**Réponse (400 Bad Request) :**
```json
{
  "timestamp": "2026-10-06T20:59:39.347620900Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Le paramètre de recherche 'q' doit contenir au moins 2 caractères.",
  "path": "/api/medications/search"
}
```

---

## 6. Lancer les tests

```bash
# Tests unitaires et d'intégration Testcontainers (22 tests au total)
mvn test
```

---

## 6. Repartir de zéro

```bash
# Arrêter et supprimer le conteneur + le volume de données
# (ACTION DESTRUCTIVE — vous perdrez toutes les données importées)
cd infra/
docker compose down -v

# Redémarrer
docker compose up -d

# Relancer l'import
```

---

## Structure du projet

```
medication-service/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/ma/secdevops/medication/
    │   │   ├── MedicationServiceApplication.java
    │   │   ├── model/Medication.java
    │   │   └── importer/
    │   │       ├── MedicationExcelReader.java
    │   │       ├── MedicationRepository.java
    │   │       ├── MedicationImportRunner.java
    │   │       └── ImportResult.java
    │   └── resources/
    │       ├── application.yml
    │       ├── application-import.yml
    │       ├── data/
    │       │   ├── ref-des-medicaments-cnops-2014.xlsx
    │       │   └── README.md
    │       └── db/migration/
    │           └── V1__create_medication.sql
    └── test/
        ├── java/.../importer/
        │   ├── MedicationCsvReader.java
        │   └── MedicationImportIntegrationTest.java
        └── resources/
            └── data/
                └── test-medications.csv
```

---

## Données

- **Source** : [data.gov.ma](https://www.data.gov.ma) — Référentiel CNOPS 2014
- **Licence** : ODbL (Open Database License)
- **5 917 médicaments** dans le fichier source
- **Règles de nettoyage** : PPV/PH/PRIX_BR = 0 → NULL, DOSAGE1 virgule → point, TAUX "70%" → 70

---

## 7. Génériques moins chers (API REST — US16)

> **Jira :** US16 — *En tant que patient, je veux voir les génériques moins chers ayant la même DCI* (EF13)

```
GET /api/medications/{code}/generiques?limit={n}      (limit : 1 à 20, 10 par défaut)
```

Un produit est proposé seulement s'il respecte **toutes** ces règles :

| Règle | Détail |
|---|---|
| Générique | `princeps_generique = 'G'` |
| Même DCI | insensible à la casse et aux accents (`AMOXICILLINE / ACIDE CLAVULANIQUE`) |
| Même dosage | converti en mg : `1 G` = `1000 MG` ; associations comparées terme à terme (`1 / 125 G / MG`) |
| Même forme | `SECABLE` ignoré, faute de la source `SUSPENTION` corrigée ; effervescent, gastro-résistant… restent différents |
| Moins cher | **prix par unité** (comprimé, gélule, sachet…) pour comparer des boîtes de tailles différentes ; flacons et tubes comparés seulement à conditionnement identique |
| Prix connu | les produits sans PPV sont ignorés |

Les résultats sont triés du moins cher au plus cher, sans doublon (même produit sous deux codes). Chaque générique
indique l'économie en % (arrondie vers le bas) et `moinsRembourse = true` s'il est moins bien remboursé que le
médicament de départ. Si la liste est vide, `message` explique pourquoi (aucun générique moins cher, dosage ou prix
absent du catalogue).

```powershell
curl.exe -s "http://localhost:8083/api/medications/6118000160182/generiques?limit=2"
```

```json
{
  "reference": { "code": "6118000160182", "nom": "AUGMENTIN", "dci": "AMOXICILLINE / ACIDE CLAVULANIQUE",
                 "dosage": "1 / 125 G / MG", "forme": "POUDRE POUR SUSPENSION BUVABLE",
                 "presentation": "1 BOITE 16 SACHET", "ppv": 168.20, "prixUnitaire": 10.51,
                 "tauxRemboursement": 70, "princepsGenerique": "P" },
  "generiques": [
    { "code": "6118000041177", "nom": "ZAMOX", "presentation": "1 BOITE 24 SACHET", "ppv": 167.00,
      "prixUnitaire": 6.96, "economiePourcent": 33, "comparaison": "PAR_UNITE",
      "tauxRemboursement": 70, "moinsRembourse": false }
  ],
  "message": null,
  "source": "Catalogue CNOPS 2014 – prix publics indicatifs"
}
```

| Code HTTP | Cas |
|---|---|
| 200 | liste (éventuellement vide, avec `message`) |
| 400 | code qui n'a pas 13 chiffres, `limit` hors de 1 à 20 |
| 404 | code absent du catalogue |

Sur le catalogue complet, 500 princeps sur 3 031 ont au moins un générique moins cher selon ces règles.
Côté Angular, le bloc `<app-generiques [code]="…">` est affiché sur la page **Médicaments** et sous chaque
médicament retenu de la page **Vérification**. Il ne donne pas de conseil médical : il invite à demander l'avis du
pharmacien ou du médecin avant de changer de médicament.

Code : `src/main/java/ma/secdevops/medication/generics/` — tests : `src/test/java/ma/secdevops/medication/generics/`
(règles, service sur des lignes réelles du CNOPS, contrôleur, requêtes PostgreSQL avec Testcontainers).
