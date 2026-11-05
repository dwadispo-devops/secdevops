# US10 — Consultation d’ordonnance et réponse du pharmacien

## A. Architecture

Le travail se trouve dans le worktree `D:\oussama\mes-projet\secdevops\.worktrees\us10`, sur la branche `feature/US10-pharmacist-prescription-response`, créée depuis `origin/develop` (`35fbdb6`). Les modifications locales de la branche `fix/us11-clean` sont préservées dans le répertoire d’origine. Aucun merge vers develop n’est effectué.

Trois services existants sont utilisés :

- **Auth Service** émet les JWT. Aucun changement de son code métier. Une page de connexion dédiée à l’espace pharmacien utilise son API réelle.
- **Pharmacy Service** possède les demandes US09, leurs pharmacies destinataires et les propriétaires US11. Il contrôle les droits, consulte les demandes et enregistre les réponses. Il reste sous Spring Boot 4.1.1.
- **Prescription Service** conserve l’image originale chiffrée d’US06 et réutilise le lecteur IA d’US07. Cette version de develop utilise encore Spring Boot 3.5.7 pour ce service ; US10 ne le migre pas artificiellement vers Boot 4.

Angular appelle les API publiques via son proxy local ou la Gateway existante. Pharmacy appelle Prescription directement, avec une délégation JWT valable 30 secondes, limitée à un identifiant d’ordonnance et à l’audience `prescription-review`. Le patient lié à la demande est déterminé dans la base, jamais depuis le formulaire pharmacien. Prescription vérifie encore que l’ordonnance appartient à ce patient.

Les routes internes ne sont pas publiées par la Gateway. Elles rejettent les JWT utilisateur ordinaires. La clé JWT partagée appartient au modèle de confiance existant entre ces microservices ; les clés restent hors Git.

## B. Backend

Dans `pharmacy-service/.../review/` :

| Fichier | Rôle |
|---|---|
| `ReviewController.java` | Expose les routes REST, récupère l’identité authentifiée, valide les DTO et sert l’image sans cache. |
| `ReviewService.java` | Vérifie l’attribution au propriétaire, applique les règles de réponse et autorise le lien avec une ordonnance du patient. |
| `ReviewRepository.java` | Lit les tables existantes avec JdbcTemplate et effectue les mises à jour conditionnelles. |
| `ReviewDtos.java` | Contrats de liste, détail, médicament, ordonnance et réponse ; enum de disponibilité et contraintes de prix/commentaire. |
| `ReviewSecurityConfig.java` | Chaîne JWT dédiée : rôle PHARMACIEN pour les demandes reçues ; rôle PATIENT pour le lien et la consultation des réponses. |
| `PrescriptionClient.java` | Client HTTP à délais bornés, délégation signée et traduction des erreurs externes en 404/503. |

Dans Prescription Service :

| Fichier | Rôle |
|---|---|
| `StoredPrescriptionController.java` | Lecture IA d’une ordonnance déjà stockée et accès interne autorisé aux informations/image. |
| `StoredReadingService.java` | Réutilise `PrescriptionReadingService`, chiffre le résultat effectif, puis expose le texte des lignes réellement lues et les médicaments reconnus. |
| `ReviewDelegationService.java` | Vérifie signature, expiration, émetteur, audience, ressource et durée de la délégation. |
| `Prescription.java` | Porte les données OCR chiffrées ; les efface lorsque le patient supprime son image. |
| `PrescriptionRepository.java` | Ajoute le verrou nécessaire pour sérialiser lecture OCR et suppression. |

Autres adaptations : propriété `PRESCRIPTION_SERVICE_URL` dans Pharmacy et son service Compose. Les règles US09/US11 et les anciennes migrations ne sont pas réécrites. Le test qui attendait exactement cinq migrations a été adapté à V6.

### Contrats REST

| Méthode et chemin | Accès / résultat |
|---|---|
| `GET /api/pharmacies/me/requests` | PHARMACIEN : 200, demandes des seules pharmacies dont il est propriétaire ; 200 demandes récentes maximum. |
| `GET /api/pharmacies/{pharmacyId}/requests/{requestId}` | PHARMACIEN propriétaire et pharmacie destinataire : détail et médicaments patient. |
| `GET .../requests/{requestId}/prescription` | Image disponible ou non, type MIME, texte OCR nullable et médicaments reconnus. |
| `GET .../requests/{requestId}/prescription/image` | Octets de l’original déchiffré, `Cache-Control: no-store`, `nosniff`. |
| `POST .../requests/{requestId}/response` | Corps ci-dessous ; renvoie la réponse enregistrée. |
| `PUT /api/availability-requests/{requestId}/prescription` | PATIENT propriétaire : `{ "prescriptionId": "UUID" }`, 204 après contrôle dans Prescription Service. |
| `GET /api/availability-requests/{requestId}/responses` | PATIENT propriétaire : état de chaque pharmacie, prix et commentaire. |
| `POST /api/v1/prescriptions/{id}/read?consent=true` | PATIENT propriétaire : appelle le lecteur IA existant et conserve son résultat chiffré. |
| `GET /internal/prescriptions/{id}` et `.../{id}/image` | Délégation serveur uniquement. |

Exemples de réponse pharmacien :

```json
{ "availability": "AVAILABLE", "price": 42.50, "comment": "Prix total de la demande" }
```

```json
{ "availability": "UNAVAILABLE", "price": null, "comment": "Produit indisponible" }
```

Le prix représente le **total en DH** pour cette demande auprès de cette pharmacie. Disponible exige un prix compris entre 0 et 9 999 999 999,99, avec deux décimales maximum. Indisponible exige un prix absent/null. Le commentaire est facultatif, limité à 1000 caractères. Les montants utilisent BigDecimal et NUMERIC, pas des flottants en base.

Aucune règle existante ne définissait les réponses répétées : la première réponse est définitive, les suivantes retournent **409**. Un verrou de demande et une mise à jour conditionnelle empêchent deux réponses concurrentes de s’écraser. Le statut est porté par l’association demande/pharmacie ; répondre dans une pharmacie ne répond pas pour les autres.

Codes : **400** saisie invalide, **401** sans JWT valide, **403** mauvais rôle, **404** ressource inexistante ou appartenant à une autre pharmacie/patient, **409** réponse répétée ou lien non modifiable, **503** service ordonnance indisponible. Le 404 identique pour les demandes étrangères évite de divulguer leur existence.

## C. Frontend

- `PharmacistRequestsPage` (`pharmacist-requests.ts/html/scss`) : route `/pharmacien/demandes`, liste des pharmacies du compte, sélection, médicaments demandés, original, texte OCR, médicaments reconnus et formulaire Disponible/Indisponible.
- `PharmacistReviewService` : appels HttpClient réels et JWT dans l’en-tête Authorization. L’identifiant fourni dans l’URL est toujours revérifié côté serveur.
- `PharmacistLoginPage` : `/pharmacien/connexion`, connexion réelle à Auth, JWT dans sessionStorage, puis navigation vers les demandes. La validation du rôle reste côté backend.
- La page d’accueil propose l’espace pharmacien. Les erreurs, l’absence d’image/OCR, la réussite et les réponses déjà enregistrées sont explicites. Les images sont chargées par HTTP authentifié et affichées via une URL Blob révoquée à la destruction/changement de sélection. Un PDF dispose d’un lien d’ouverture protégé.
- US09 reçoit uniquement un champ facultatif de référence d’ordonnance. Après création, le patient peut partager cette ordonnance grâce au nouvel endpoint. Si le lien échoue, l’écran indique clairement que la demande existe déjà et ne doit pas être renvoyée.
- Après un scan US06, un bouton permet d’appeler la lecture IA existante sur l’original enregistré. Aucun catalogue fictif d’un autre écran ni résultat OCR de démonstration n’est utilisé par US10.

L’envoi est bloqué pour un prix négatif ou invalide. Indisponible envoie toujours `price: null`. Les doubles clics et changements de sélection pendant l’envoi sont empêchés. Les anciennes requêtes de détail/image sont annulées quand une autre demande est choisie.

## D. Base de données

Les tables US09 `availability_requests`, `availability_request_items`, `availability_request_pharmacies`, ainsi que `pharmacies.owner_user_id` d’US11, sont réutilisées.

**Pharmacy V6** ajoute `prescription_id` à la demande et `price`, `comment`, `responded_at`, `responded_by` à son association pharmacie. Une contrainte SQL vérifie la cohérence statut/prix/auteur/date. Aucun ancien contenu n’est supprimé.

**Prescription V2** ajoute `reading_iv` et `reading_data` à `prescription`. La sortie IA est chiffrée avec le service AES-256-GCM déjà utilisé pour les images. Les images restent dans `prescription_image`. Les migrations V1 à V5 existantes sont inchangées.

La pile de vérification utilise un **volume de démonstration distinct**, avec trois bases `auth_us10`, `pharmacy_us10`, `prescription_us10`. Les volumes PostgreSQL précédents ne sont ni montés ni modifiés par ces tests. Les images Docker déjà présentes sont réutilisées avec `pull_policy: never`.

## E. Tests et vérifications

| Vérification réellement exécutée | Résultat |
|---|---|
| Pharmacy : `mvnw.cmd test package` | **110 tests, 0 échec, 0 erreur, 0 ignoré**, JAR produit. |
| Prescription : `mvn test` | **73 tests, 0 échec, 0 erreur, 0 ignoré** ; JAR produit ensuite sans répéter les tests. |
| Angular : `npm run build` | Succès production ; avertissements existants CSS pharmacies et Leaflet CommonJS. |
| Angular : `npm test -- --watch=false` | **70 tests réussis**. |
| `scripts/test-us10-http.ps1` | Succès avec vrais JWT Auth : inscription synthétique, rôles locaux, demande US09, lien ordonnance, original déchiffré, réponses disponible/indisponible, consultation patient, refus 400/401/403/404/409. |
| `scripts/test-us10-browser.ps1` | Succès Chrome réel : formulaire de connexion, liste, original affiché, absence OCR explicite, prix négatif bloqué, réponse à 19,50 DH, confirmation et lecture du même prix par le patient. |
| Migration réelle | Pharmacy V6 et Prescription V2 appliquées au démarrage des services Java 21 isolés. |

Les tests couvrent aussi : demande inconnue, changement d’identité, usurpation d’un identifiant de pharmacie, absence de prescription, image supprimée, service distant indisponible, zéro comme prix valide, trop de décimales, absence de prix, réponses concurrentes (exactement un 200 et un 409), contrôle du patient lors du partage et tokens internes invalides/expirés/mal ciblés.

**Limite vérifiée : aucun fournisseur IA réel n’a été validé pour cette démonstration.** `GEMINI_API_KEY` est volontairement non configurée dans `.us10/demo.env`. Les tests de conservation et d’affichage d’un texte OCR existant utilisent un lecteur simulé uniquement dans les tests. Les tests HTTP/navigateur réels utilisent une image blanche synthétique et attendent un OCR absent ; ils n’injectent aucun faux texte OCR dans les services de démonstration.

Avec une vraie clé/modèle accessibles, le bouton de lecture appelle le fournisseur US07 existant et conserve son résultat réel. Cette dépendance externe reste à vérifier dans l’environnement du professeur. La branche distante `DEVOPS-19-us07-rag` n’a pas été fusionnée : US10 part de develop, sans introduire une seconde implémentation IA.

Les journaux `us10-*.log`, les JAR, node_modules, screenshots, profils Chrome, `.us10/demo.env` et `.us10/accounts.json` sont locaux et exclus du commit. Les compilations locales ont utilisé le JDK complet installé ; l’exécution réelle des trois services a utilisé **Java 21** via l’image Maven existante.

## F. Démonstration Sprint Review

### Démarrer les services déjà compilés

Dans PowerShell :

```powershell
cd D:\oussama\mes-projet\secdevops\.worktrees\us10
.\scripts\prepare-us10-demo.ps1
docker compose --env-file .us10/demo.env -f compose.us10-demo.yaml up -d --no-build --pull never
Invoke-RestMethod http://localhost:18082/actuator/health
Invoke-RestMethod http://localhost:18085/actuator/health
```

Dans un autre terminal, uniquement si le serveur Angular n’est plus actif :

```powershell
cd D:\oussama\mes-projet\secdevops\.worktrees\us10\frontend
npm.cmd start -- --port 4201 --proxy-config proxy.us10.json
```

La démonstration tourne sur **http://localhost:4201** pour préserver le frontend précédent sur 4200. Pharmacy est exposé sur 18082, Prescription sur 18085, Auth sur 18081 et PostgreSQL sur 5540. Les ports internes restent respectivement 8082, 8085, 8081 et 5432. Gateway et Eureka ne sont pas nécessaires avec ce proxy local ; les routes publiques restent compatibles avec la Gateway existante.

### Préparer des données synthétiques et jouer le scénario

```powershell
cd D:\oussama\mes-projet\secdevops\.worktrees\us10
.\scripts\test-us10-http.ps1
$accounts = Get-Content .us10/accounts.json -Raw | ConvertFrom-Json
```

Ce script ajoute ses propres comptes synthétiques et attribue le rôle PHARMACIEN ainsi que la propriété d’une pharmacie **uniquement dans la pile isolée**. Il ne crée aucun mécanisme d’élévation de rôle dans l’application. Le lien propriétaire n’étant pas renseigné par l’ancienne inscription de pharmacie, cette préparation administrative explicite est nécessaire. Ne pas exécuter ce provisionnement contre une base de production.

1. Ouvrir `http://localhost:4201/pharmacien/connexion`.
2. Utiliser l’e-mail et le mot de passe du compte `pharmacist` dans le fichier local `.us10/accounts.json`. Ce fichier n’est pas dans Git ; ne pas le partager publiquement.
3. Ouvrir la demande « En attente » de cette pharmacie.
4. Montrer les médicaments patient et l’image originale blanche **de test**. Expliquer honnêtement le message « Aucun texte OCR disponible » si aucun fournisseur IA n’est configuré.
5. Choisir Disponible, saisir un prix tel que `19.50`, ajouter un commentaire et envoyer. Montrer la confirmation et la réponse figée.
6. Pour une autre demande fraîche, choisir Indisponible : aucun prix n’est envoyé. Le script HTTP peut être rejoué pour préparer une nouvelle demande en attente ; il ne supprime aucune donnée.
7. Montrer qu’un JWT patient ou un autre pharmacien ne peut pas répondre à cette demande.

Le contrôle automatisé Chrome peut être rejoué avec `.\scripts\test-us10-browser.ps1` après le script HTTP. Il consomme une demande en attente puis en prépare une nouvelle pour la présentation. Les preuves locales sont dans `.us10/browser-results.json` et `.us10/browser-success.png`.

### Tester les endpoints en PowerShell

```powershell
$accounts = Get-Content .us10/accounts.json -Raw | ConvertFrom-Json
$body = @{ email = $accounts.pharmacist.email; password = $accounts.pharmacist.password } | ConvertTo-Json
$session = Invoke-RestMethod -Method Post http://localhost:18081/api/auth/login -ContentType application/json -Body $body
$headers = @{ Authorization = "Bearer $($session.accessToken)" }
$list = Invoke-RestMethod http://localhost:18082/api/pharmacies/me/requests -Headers $headers
$selected = $list | Where-Object requestId -eq $accounts.pendingRequestId | Select-Object -First 1
$url = "http://localhost:18082/api/pharmacies/$($selected.pharmacyId)/requests/$($selected.requestId)"
Invoke-RestMethod $url -Headers $headers
Invoke-RestMethod "$url/prescription" -Headers $headers
Invoke-WebRequest -UseBasicParsing "$url/prescription/image" -Headers $headers -OutFile .us10/review-image.png
$answer = @{ availability = 'AVAILABLE'; price = 42.50; comment = 'Prix total' } | ConvertTo-Json
Invoke-RestMethod -Method Post "$url/response" -Headers $headers -ContentType application/json -Body $answer
```

Résultat attendu : HTTP 200 avec `status: AVAILABLE`, prix et date d’enregistrement. La même requête envoyée à nouveau retourne 409. Pour Indisponible, utiliser une demande encore en attente et `@{ availability = 'UNAVAILABLE'; price = $null }`.

Le patient consulte ensuite les réponses avec son propre JWT :

```powershell
$body = @{ email = $accounts.patient.email; password = $accounts.patient.password } | ConvertTo-Json
$patient = Invoke-RestMethod -Method Post http://localhost:18081/api/auth/login -ContentType application/json -Body $body
Invoke-RestMethod "http://localhost:18082/api/availability-requests/$($selected.requestId)/responses" -Headers @{ Authorization = "Bearer $($patient.accessToken)" }
```

### Recompiler après un nouveau checkout

Les JAR de cette session sont déjà présents. Pour reconstruire ailleurs avec un JDK 21 complet et Maven Wrapper :

```powershell
cd D:\oussama\mes-projet\secdevops\.worktrees\us10\pharmacy-service
# Definir JAVA_HOME vers le JDK complet installe si necessaire.
.\mvnw.cmd test package
.\mvnw.cmd -f ../prescription-service/pom.xml test package
.\mvnw.cmd -f ../auth-service/pom.xml -DskipTests package
cd ..\frontend
npm.cmd ci
npm.cmd run build
npm.cmd test -- --watch=false
```

Docker Desktop doit être actif pour les tests Pharmacy/Testcontainers. `compose.us10-demo.yaml` ne télécharge aucune image : si une image manque sur une autre machine, l’utilisateur doit la préparer explicitement. Conserver `.us10/demo.env` avec son volume ; le régénérer seul changerait les mots de passe et les clés de déchiffrement.

## G. Git et Merge Request

Branche : **feature/US10-pharmacist-prescription-response**. Le commit final et la confirmation du push sont donnés dans la réponse de livraison ; ils se vérifient avec :

```powershell
cd D:\oussama\mes-projet\secdevops\.worktrees\us10
git branch --show-current
git log -1 --oneline
git status --short
git ls-remote origin refs/heads/feature/US10-pharmacist-prescription-response
git diff --stat origin/develop...HEAD
```

Dans GitLab : ouvrir **Merge requests → New merge request**, sélectionner la branche US10 comme source et `develop` comme cible. Relire les contrôles de droits, les migrations V6/V2, le contrat de prix total et de réponse définitive, ainsi que la limite du fournisseur OCR non configuré. Vérifier le pipeline et l’absence de secrets/données médicales dans le diff. La Merge Request et son merge restent manuels.
