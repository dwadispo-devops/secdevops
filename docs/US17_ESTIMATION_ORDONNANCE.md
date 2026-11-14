# US17 — Estimation indicative de l'ordonnance

## Périmètre et règles livrées

Sur `/verification`, le patient vérifie chaque produit, corrige sa quantité et confirme qu'elle correspond au nombre de **conditionnements** du produit sélectionné (boîtes, flacons…), pas au nombre de comprimés. Une quantité absente reste vide : aucun remplacement par une boîte n'est effectué pour l'estimation. Toute nouvelle lecture, modification du produit ou de la quantité demande une nouvelle confirmation de quantité.

Le bouton « Estimer le coût » devient disponible lorsque toutes les lignes non supprimées ont un produit avec code catalogue de 13 chiffres, une quantité entière de 1 à 10000 et une confirmation. Ces bornes, ainsi que la limite de 100 lignes par estimation, sont des protections techniques, pas des recommandations de prescription. Le bouton « Continuer » et le parcours de disponibilité existants conservent leurs règles précédentes ; ces confirmations ne les bloquent pas.

Pour chaque ligne :

```text
Coût estimé = quantité confirmée × PPV du code exact
```

Le backend vérifie que le catalogue contient un PPV non négatif et une présentation non vide, et que l'unité a été confirmée. La présentation renvoyée dans le détail permet de vérifier la correspondance. L'application n'interprète aucune posologie et ne convertit pas des comprimés en boîtes.

Le prix provient toujours du serveur. Aucun prix hospitalier ni `prix_br` ne remplace un PPV absent. Les montants sont calculés avec `BigDecimal`. Le PPV stocké a deux décimales, la quantité est entière : la multiplication est exacte et n'exige pas de règle d'arrondi de remboursement. Le résultat est présenté avec deux décimales, en MAD.

Si des lignes sont inconnues ou non calculables, `complete=false`, `totalEstimatedCost=null` et le sous-total des seules lignes calculables est explicitement identifié. Un prix inconnu n'est jamais présenté comme gratuit.

**Remboursement et reste à charge : non calculables dans cette version.** Le taux existant est affiché comme « Taux catalogue », avec la distinction entre zéro et absence de donnée. La base de remboursement n'étant pas confirmée, ni PPV ni `prix_br` ne sont arbitrairement utilisés à sa place. Les deux montants correspondants restent `null`, y compris pour un taux catalogue nul. Aucun régime personnel n'est déduit.

L'interface signale que le référentiel est CNOPS 2014 et que les prix peuvent avoir changé. Il ne s'agit ni d'un devis de pharmacie ni d'une garantie de remboursement. Les prix globaux des pharmacies et la réservation US12 ne sont pas modifiés.

## Architecture et API

```text
Angular /verification
  → Gateway /api/v1/prescriptions/estimate
  → prescription-service : JWT patient, propriété éventuelle, calcul
  → medication-service /api/medications/lookup
  → SELECT par codes exacts dans la base du catalogue
```

`POST /api/medications/lookup` accepte `{ "codes": ["code de 13 chiffres"] }`, de 1 à 100 codes. La réponse renvoie les produits trouvés, sans rapprochement approximatif ; les codes inconnus sont absents. Les doublons de recherche sont supprimés. L'API de catalogue reste publique comme `/search` et ne reçoit aucun identifiant patient, image ou texte OCR. Les requêtes SQL sont paramétrées. La réponse n'expose pas `prix_br` ni le prix hospitalier.

`POST /api/v1/prescriptions/estimate` nécessite un JWT valide de rôle PATIENT :

```json
{
  "prescriptionId": null,
  "items": [
    { "code": "6118000000001", "quantity": 2, "unitConfirmed": true }
  ]
}
```

Le code ci-dessus illustre le format et n'est pas une donnée à injecter dans le catalogue. Angular envoie la référence de la dernière ordonnance scannée lorsqu'elle existe. Le backend vérifie alors qu'elle appartient au patient ; une ordonnance absente ou étrangère donne le même 404. Sans référence, le calcul porte uniquement sur les lignes fournies : il ne certifie pas leur présence dans une ordonnance enregistrée.

La réponse contient `currency`, `source`, `calculatedAt`, les lignes détaillées, `complete`, `knownSubtotal`, `totalEstimatedCost`, `reimbursableAmount`, `remainingCharge` et l'explication de remboursement. Aucun montant transmis par le navigateur n'est utilisé pour calculer. Une quantité décimale est rejetée, sans troncature silencieuse. Aucun contenu d'ordonnance n'est ajouté aux journaux par le nouveau code.

Erreurs : 400 pour entrée invalide, 401 pour JWT absent/invalide/expiré, 403 pour rôle non patient, 404 pour ordonnance inaccessible, 503 pour catalogue inaccessible ou expiration de son délai de réponse (3 secondes par défaut). Une absence de produit dans un catalogue disponible produit une ligne non calculable, pas un faux prix. Les deux réponses d'API portent `Cache-Control: no-store`.

L'estimation est conservée uniquement dans l'état du composant Angular. Un changement des lignes annule la requête en cours et efface le résultat précédent ; le patient relance le calcul. Un rechargement de page ne restaure ni l'estimation ni les corrections en mémoire. Aucune migration SQL, table, configuration Docker ou dépendance supplémentaire n'est nécessaire.

## Fichiers créés et modifiés

Tous les chemins sont relatifs à `D:\oussama\mes-projet\secdevops`.

| État | Chemin exact | Rôle |
|---|---|---|
| Créé | `medication-service/src/main/java/ma/secdevops/medication/controller/MedicationFinancialController.java` | Endpoint groupé, validation et erreurs 400 spécifiques. |
| Créé | `medication-service/src/main/java/ma/secdevops/medication/dto/MedicationFinancialResponse.java` | Contrat des données financières de catalogue. |
| Créé | `medication-service/src/main/java/ma/secdevops/medication/repository/MedicationFinancialRepository.java` | Lecture SQL paramétrée par codes exacts. |
| Créé | `medication-service/src/test/java/ma/secdevops/medication/controller/MedicationFinancialControllerTest.java` | Contrat HTTP, doublons et entrées invalides. |
| Créé | `medication-service/src/test/java/ma/secdevops/medication/repository/MedicationFinancialIntegrationTest.java` | Vraie base PostgreSQL temporaire, nulls, absence de recherche approximative et paramétrage SQL. |
| Créé | `prescription-service/src/main/java/ma/ensa/pharma/prescription/api/PrescriptionEstimateController.java` | API patient et réponse non mise en cache. |
| Créé | `prescription-service/src/main/java/ma/ensa/pharma/prescription/api/PrescriptionEstimateErrors.java` | Erreurs 400 sans exposer les valeurs soumises ; limité à US17. |
| Créé | `prescription-service/src/main/java/ma/ensa/pharma/prescription/api/dto/PrescriptionEstimateDtos.java` | Entrées, sorties et lecture stricte des quantités entières. |
| Créé | `prescription-service/src/main/java/ma/ensa/pharma/prescription/catalog/FinancialCatalogClient.java` | Appel groupé au catalogue, délais et erreurs 503. |
| Créé | `prescription-service/src/main/java/ma/ensa/pharma/prescription/service/PrescriptionEstimateService.java` | Propriété de l'ordonnance, coûts exacts, sous-total et remboursement non calculable. |
| Créé | `prescription-service/src/test/java/ma/ensa/pharma/prescription/api/PrescriptionEstimateIntegrationTest.java` | HTTP, validation, vrai JWT et vrai service de calcul ; catalogue et repository simulés. |
| Créé | `prescription-service/src/test/java/ma/ensa/pharma/prescription/catalog/FinancialCatalogClientTest.java` | Contrat HTTP interservices, données absentes, panne et timeout simulés. |
| Créé | `prescription-service/src/test/java/ma/ensa/pharma/prescription/service/PrescriptionEstimateServiceTest.java` | Calculs, cas incomplets, unité et contrôle de propriété. |
| Modifié | `frontend/src/app/services/ordonnance.ts` | Correction des quantités et confirmation réinitialisée après modification. |
| Modifié | `frontend/src/app/services/ordonnance.spec.ts` | Vérification de la conservation d'une quantité inconnue et des confirmations. |
| Créé | `frontend/src/app/services/prescription-estimate.service.ts` | Types et appel authentifié à l'API d'estimation. |
| Créé | `frontend/src/app/components/prescription-estimate.ts` | Affichage, contrôle des lignes, calcul explicite, erreurs et annulation des requêtes anciennes. |
| Créé | `frontend/src/app/components/prescription-estimate.spec.ts` | Contrat envoyé, montants, états incomplets, erreurs, corrections et annulation. |
| Modifié | `frontend/src/app/pages/verification.ts` | Édition/confirmation des quantités et intégration du composant. |
| Modifié | `frontend/src/app/pages/verification.spec.ts` | Test des vrais champs de quantité et confirmation dans Angular. |
| Modifié | `medication-service/README.md` | Lien vers les contrats et la recette US17. |
| Modifié | `prescription-service/README.md` | Lien vers le fonctionnement du calcul US17. |
| Modifié | `frontend/README.md` | Lien vers le parcours et les tests US17. |
| Créé | `docs/US17_ESTIMATION_ORDONNANCE.md` | Présente documentation et guide de recette. |

## Commandes PowerShell de démarrage

Pré requis : Docker Desktop démarré en mode Linux, Node/npm compatibles avec le frontend, ports libres. Le `.env` racine doit déjà contenir les valeurs prévues par Docker Compose : `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `INTERNAL_API_TOKEN`, `PRESCRIPTION_IMAGE_KEY`. Une vraie `GEMINI_API_KEY` est nécessaire pour tester le parcours scan puis lecture IA ; l'estimation elle-même n'appelle pas Gemini.

Depuis un terminal :

```powershell
Set-Location 'D:\oussama\mes-projet\secdevops'
docker compose up -d --build
docker compose ps
Invoke-RestMethod 'http://localhost:8083/actuator/health'
Invoke-RestMethod 'http://localhost:8085/actuator/health'
```

Attendre que les services soient prêts et enregistrés dans Eureka (`http://localhost:8761`). La Gateway est sur `8080`, auth sur `8081`, pharmacy sur `8082`, medication sur `8083`, prescription sur `8085`. Le catalogue et prescription doivent tous deux être reconstruits pour disposer des nouveaux endpoints.

Vérifier que le catalogue est déjà chargé :

```powershell
Invoke-RestMethod 'http://localhost:8080/api/medications/search?q=dolip&limit=5'
```

Si le référentiel n'a jamais été importé dans cette base locale, l'import existant peut être lancé avec l'image construite :

```powershell
docker compose run --rm --no-deps medication-service --spring.profiles.active=import
```

Cette commande est une préparation **manuelle facultative** qui écrit le référentiel dans la base locale configurée. Elle n'a pas été exécutée sur les bases applicatives pendant cette implémentation. Ne pas la lancer sur une base dont les prix ont été personnalisés sans vérifier l'effet de l'import idempotent existant.

Dans un second terminal :

```powershell
Set-Location 'D:\oussama\mes-projet\secdevops\frontend'
npm ci
npm start
```

Ouvrir `http://localhost:4200`. Le proxy Angular envoie `/api/**` à la Gateway.

## Recette manuelle depuis Angular

1. Ouvrir `/connexion` avec un compte patient (ou créer le compte sur `/inscription`).
2. Ouvrir `/scan`, choisir une ordonnance réelle de test lisible (JPG, PNG ou PDF accepté), donner le consentement et cliquer sur « Envoyer mon ordonnance ».
3. Après l'envoi, cliquer sur « Lire mon ordonnance et vérifier les médicaments ». Attendre la lecture IA et l'ouverture de `/verification`.
4. Vérifier les produits retenus. Pour une ligne incertaine, choisir la bonne suggestion ou utiliser la recherche catalogue. Supprimer uniquement les lignes à exclure du test.
5. Dans chaque ligne restante, saisir une quantité entière, par exemple `2`, **uniquement si elle correspond à l'ordonnance de test**, puis cocher « Je confirme la quantité et sa correspondance avec le conditionnement du médicament retenu ». Si la quantité n'était pas lue, le champ doit être vide au départ.
6. Cliquer sur « Estimer le coût ». Résultat attendu : détail de chaque produit et de sa présentation, quantité, PPV, coût de ligne et taux catalogue. Le total doit être la somme des `PPV × quantités` réellement renvoyés ; aucun montant fixe n'est à supposer pour le catalogue local.
7. Vérifier les textes « Remboursement non calculable » et « Reste à charge : non calculable », avec l'explication de base non confirmée. Même pour un taux de 70 %, aucun montant remboursé ne doit être inventé.
8. Modifier une quantité : l'ancien résultat doit disparaître, la confirmation doit être décochée. Reconfirmer puis relancer le calcul. Pour un PPV inchangé, le nouveau coût doit suivre exactement la nouvelle quantité.
9. Corriger le produit vers un autre conditionnement : la confirmation doit également être demandée à nouveau. Supprimer/rétablir une ligne doit invalider le résultat précédent.
10. Saisir une quantité vide, zéro, négative ou décimale : le bouton d'estimation doit rester désactivé. Supprimer toutes les lignes : aucun total vide ne doit être présenté comme une ordonnance estimée.
11. Tester un produit du vrai catalogue sans PPV, s'il existe dans les données de test disponibles : sa ligne doit être non calculable, sans utilisation de PH ou de `prix_br`. Le résultat global doit annoncer une estimation incomplète et un sous-total connu. Ne pas modifier les prix applicatifs pour fabriquer ce cas ; il est aussi couvert dans une base temporaire automatisée.
12. Dans F12 → Réseau, observer `POST /api/v1/prescriptions/estimate`. La requête doit contenir uniquement la référence éventuelle et les codes/quantités/confirmations ; pas de prix, nom de patient ni image. La réponse doit avoir `Cache-Control: no-store` et les deux montants de remboursement/reste à charge à `null`.
13. Choisir Network → Offline puis cliquer sur « Estimer le coût » : un message d'échec doit remplacer le résultat, sans montant de secours. Repasser Online et réessayer : le résultat doit revenir. Une modification pendant la requête doit empêcher l'affichage du résultat ancien.
14. Sans recharger la page, retirer le jeton du profil patient de test avec `sessionStorage.removeItem('access_token')`, puis relancer le calcul : un lien de connexion doit apparaître. Se reconnecter et recalculer. Recharger complètement `/verification` réinitialise les données en mémoire ; il faut alors reprendre le scan, conformément au périmètre sans persistance.
15. Cliquer sur « Continuer » après le test : la demande de disponibilité doit reprendre les quantités corrigées selon le parcours existant. Avec un compte pharmacien dans un autre profil, répondre Disponible avec un prix ; vérifier ensuite côté patient la réservation US12, son QR et sa validité de deux heures. Le prix du pharmacien peut différer du PPV estimé et n'est pas remplacé par ce total.

Une réponse 404 sur l'estimation peut signaler une ancienne référence `last_prescription_id` appartenant à un autre compte : reprendre le scan avec le patient courant, sans contourner le contrôle de propriété. La saisie manuelle avec estimation et l'historique ne font pas partie de cette version.

## Tests automatisés et portée

Depuis la racine, utiliser un JDK complet compatible avec la compilation Java 21. Le wrapper déjà présent dans pharmacy-service est réutilisé ; aucun wrapper ni pom n'est modifié :

```powershell
# Définir JAVA_HOME vers votre JDK complet si nécessaire.
.\pharmacy-service\mvnw.cmd -f prescription-service/pom.xml test
.\pharmacy-service\mvnw.cmd -f medication-service/pom.xml test
.\pharmacy-service\mvnw.cmd -f pharmacy-service/pom.xml '-Dtest=ReservationIntegrationTest,ReservationRetraitIntegrationTest,PickupCodeGeneratorTest,NotificationIntegrationTest' test
Push-Location frontend
npm test -- --watch=false
npm run build
Pop-Location
```

Sur la machine de développement, seul le JDK JetBrains Runtime 25.0.4 complet a été utilisé, avec compilation `release 21`. Les dépendances de test anciennes de medication-service ont initialement refusé Java 25 et l'API Docker récente. Le lancement suivant a permis d'exécuter **tous** ses tests sans modifier les dépendances ni les paramètres Docker globaux :

```powershell
$env:JAVA_HOME = 'D:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\jbr'
.\pharmacy-service\mvnw.cmd -f medication-service/pom.xml '-DargLine=-Dnet.bytebuddy.experimental=true -Dapi.version=1.44' test
```

Les tests PostgreSQL démarrent leurs bases temporaires avec Testcontainers ; aucune base applicative n'a été réinitialisée. Le test HTTP d'estimation utilise le vrai contrôle JWT et le vrai service de calcul, avec catalogue et repository isolés par des mocks. Le client interservices est testé avec un faux serveur HTTP et le SQL du catalogue avec une vraie base temporaire. Un parcours navigateur avec tous les services réels reste à valider manuellement ; aucune réussite de ce parcours n'est revendiquée par les tests MockMvc/Vitest.

Les journaux locaux de test sont sous `prescription-service/target/us17-tests.log`, `medication-service/target/us17-tests.log` et `pharmacy-service/target/us17-us12-regression.log`. Les rapports backend sont dans les répertoires `target/surefire-reports/` respectifs. Ces artefacts ne sont pas destinés à être versionnés.

### Résultats exécutés le 9 novembre 2026

| Vérification | Résultat |
|---|---|
| Suite complète prescription-service | 148 tests réussis, aucun échec/erreur/test ignoré |
| Suite complète medication-service | 42 tests réussis, aucun échec/erreur/test ignoré |
| Régression réservation/retrait/notifications pharmacy-service | 39 tests réussis, dont les 21 tests US13 inchangés |
| Suite complète Angular, après le dernier changement de confirmation | 139 tests réussis dans 16 fichiers |
| Build Angular production | Réussi avec les avertissements existants CSS/CommonJS |
| Vérification des espaces avec `git diff --check` | Réussie |

Les nouveaux tests backend sont au nombre de 14 pour prescription-service et 4 pour medication-service ; les autres tests sont les régressions existantes. Les tests de prix utilisent des données explicitement synthétiques dans des doubles de test ou une base temporaire ; aucune valeur n'est imposée au catalogue applicatif.

Les premiers essais ont révélé un import Java manquant dans un nouveau test (corrigé), ainsi que les incompatibilités de l'outillage ancien avec Java 25/Docker récent (contournées localement avec les options documentées). La recette navigateur avec services réels, comptes réels et lecture IA n'a pas été exécutée et reste à valider par l'utilisateur.

## Limites restantes

- Aucun remboursement personnel calculé : la base métier n'est pas confirmée.
- Prix indicatifs du référentiel existant, sans certification d'actualité ni connexion à un organisme payeur.
- Correspondance quantité/conditionnement confirmée par le patient ; aucune interprétation médicale automatique.
- Aucune persistance, aucun historique ni restauration après rechargement.
- Le parcours scan dépend de la disponibilité et de la configuration IA existantes. US17 ne fournit pas de résultat fictif si cette étape échoue.
- Le build frontend peut conserver les avertissements existants sur le budget CSS de pharmacies et les modules CommonJS Leaflet/qrcode d'US12.
- Aucun commit, push, merge ou changement de branche n'est réalisé automatiquement. La recette manuelle doit être validée par l'utilisateur avant toute livraison Git.
