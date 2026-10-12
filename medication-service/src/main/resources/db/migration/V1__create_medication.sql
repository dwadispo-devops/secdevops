-- ============================================================
-- V1__create_medication.sql
-- Migration initiale : création de la table medication
-- Référentiel des médicaments CNOPS (data.gov.ma)
-- ============================================================

-- Extension pour la recherche full-text par trigrammes (autocomplétion sur NOM)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ============================================================
-- Table principale : medication
-- ============================================================
CREATE TABLE IF NOT EXISTS medication (
    id                  BIGSERIAL       PRIMARY KEY,

    -- Identifiant produit : code-barres EAN-13 (13 chiffres, gardé en texte)
    code                VARCHAR(13)     NOT NULL UNIQUE,

    -- Nom commercial du médicament
    nom                 VARCHAR(255)    NOT NULL,

    -- Dénomination Commune Internationale (DCI)
    dci1                VARCHAR(255),

    -- Dosage (ex : 200, 0.25, 3000) — NULL si non renseigné (UNITE = 'NA')
    dosage1             NUMERIC(12, 3),

    -- Unité du dosage (ex : MG, ML, NA)
    unite_dosage1       VARCHAR(20),

    -- Forme pharmaceutique (ex : COMPRIME PELLICULE, SOLUTION BUVABLE)
    forme               VARCHAR(150),

    -- Présentation / conditionnement (ex : 1 BOITE 20 COMPRIME)
    presentation        VARCHAR(255),

    -- Prix Public de Vente en MAD — NULL si non applicable (médicament hospitalier)
    ppv                 NUMERIC(10, 2),

    -- Prix Hospitalier en MAD — NULL si non applicable (médicament en officine uniquement)
    ph                  NUMERIC(10, 2),

    -- Prix de Revient Brut en MAD — NULL si donnée manquante
    prix_br             NUMERIC(10, 2),

    -- P = Princeps (médicament de référence), G = Générique
    princeps_generique  CHAR(1)         CHECK (princeps_generique IN ('P', 'G')),

    -- Taux de remboursement CNOPS en pourcentage entier (0 ou 70)
    taux_remboursement  SMALLINT,

    -- Horodatages
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- ============================================================
-- Index
-- ============================================================

-- Index GIN trigramme sur NOM pour l'autocomplétion rapide
-- (recherche partielle : "dolip" → "DOLIPRANE")
CREATE INDEX IF NOT EXISTS idx_medication_nom_trgm
    ON medication USING GIN (nom gin_trgm_ops);

-- Index BTree sur lower(dci1) pour la recherche de génériques par DCI
-- (ex : lower(dci1) = 'oxaliplatine')
CREATE INDEX IF NOT EXISTS idx_medication_dci1_lower
    ON medication (lower(dci1));

-- Index sur princeps_generique pour filtrer P/G rapidement
CREATE INDEX IF NOT EXISTS idx_medication_pg
    ON medication (princeps_generique);

-- Index sur taux_remboursement pour filtrer les médicaments remboursés
CREATE INDEX IF NOT EXISTS idx_medication_taux
    ON medication (taux_remboursement);

-- ============================================================
-- Commentaires de documentation
-- ============================================================
COMMENT ON TABLE medication IS 'Référentiel des médicaments CNOPS (source : data.gov.ma, 2014)';
COMMENT ON COLUMN medication.code IS 'Code-barres EAN-13, conservé en texte pour éviter la troncature';
COMMENT ON COLUMN medication.ph IS 'NULL si le médicament est vendu en officine uniquement (non hospitalier)';
COMMENT ON COLUMN medication.ppv IS 'NULL si le médicament est hospitalier sans prix public (PPV=0 dans la source)';
COMMENT ON COLUMN medication.prix_br IS 'NULL si la donnée est manquante dans la source (PRIX_BR=0)';
COMMENT ON COLUMN medication.taux_remboursement IS 'Valeurs possibles : 0 (non remboursé) ou 70 (remboursé à 70%)';
