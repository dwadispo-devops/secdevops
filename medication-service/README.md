# medication-service

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

## 5. Lancer les tests

```bash
# Tests d'intégration Testcontainers (nécessite Docker)
mvn test

# Tests uniquement
mvn test -pl medication-service
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
