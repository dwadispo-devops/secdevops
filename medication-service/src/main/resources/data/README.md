# Données – Référentiel des médicaments CNOPS

## Source
- **Origine** : [data.gov.ma](https://www.data.gov.ma) — Portail national des données ouvertes du Maroc
- **Jeu de données** : Référentiel des médicaments CNOPS (2014)
- **Licence** : [Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/)
- **Date de téléchargement** : Octobre 2026
- **Format d'origine** : Excel (.xlsx)
- **Fichier** : `ref-des-medicaments-cnops-2014.xlsx`

## Contenu
- **5 917 médicaments** avec 12 colonnes : CODE, NOM, DCI1, DOSAGE1, UNITE_DOSAGE1, FORME, PRESENTATION, PPV, PH, PRIX_BR, PRINCEPS_GENERIQUE, TAUX_REMBOURSEMENT
- **Aucun doublon** de CODE dans le fichier source
- **Taux de remboursement** : uniquement 0% et 70%

## Règles de nettoyage appliquées à l'import
| Valeur source | Valeur importée |
|--------------|-----------------|
| `PPV = 0` | `NULL` (208 médicaments sans prix public) |
| `PH = 0` | `NULL` (421 médicaments non hospitaliers) |
| `PRIX_BR = 0` | `NULL` (27 données manquantes) |
| `DOSAGE1 = None` | `NULL` (181 médicaments sans dosage, UNITE='NA') |
| `DOSAGE1` avec virgule | Converti en décimal (ex. `'0,25'` → `0.25`) |
| `TAUX_REMBOURSEMENT = '70%'` | `70` |
| `TAUX_REMBOURSEMENT = '0%'` | `0` |

## Mise à jour
Pour mettre à jour le référentiel, remplacer ce fichier et relancer l'import (idempotent).
