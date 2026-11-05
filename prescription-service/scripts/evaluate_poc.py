#!/usr/bin/env python3
"""
Envoie chaque ordonnance fictive de samples/ au service et mesure (US07 / TS06) :
  - le taux de médicaments reconnus dans le catalogue (1re suggestion = bon médicament) ;
  - la répartition des couleurs (vert >= 0,85, orange 0,60-0,85, rouge < 0,60) ;
  - les quantités lues ;
  - la résistance à l'injection de prompt (aucun produit inventé).

Pré-requis : prescription-service (8085) ET medication-service (8083) démarrés, catalogue CNOPS importé.
Usage : python3 scripts/evaluate_poc.py [http://localhost:8085] [dossier_samples]
Dépendance : pip install requests
"""
import json
import sys
import unicodedata
from pathlib import Path

import requests

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8085"
SAMPLES = Path(sys.argv[2] if len(sys.argv) > 2 else "samples")


def norm(s: str) -> str:
    s = unicodedata.normalize("NFKD", s or "").encode("ascii", "ignore").decode()
    return "".join(ch for ch in s.lower() if ch.isalnum())


def couleur(score: float) -> str:
    return "vert" if score >= 0.85 else "orange" if score >= 0.60 else "rouge"


def main():
    total = found = qty_total = qty_ok = failures = 0
    couleurs = {"vert": 0, "orange": 0, "rouge": 0}
    for exp_file in sorted(SAMPLES.glob("*.expected.json")):
        exp = json.loads(exp_file.read_text(encoding="utf-8"))
        img = SAMPLES / exp["file"]
        with img.open("rb") as fh:
            r = requests.post(f"{BASE}/api/v1/prescriptions/read",
                              files={"file": (img.name, fh, "image/png")},
                              data={"consent": "true"}, timeout=60)
        if r.status_code != 200:
            print(f"[ERREUR] {img.name}: HTTP {r.status_code} {r.text[:200]}")
            failures += 1
            continue
        body = r.json()
        lignes = body["lignes"]
        for l in lignes:
            couleurs[couleur(l["score"])] += 1

        ok = 0
        for med in exp["expectedMedications"]:
            total += 1
            # La ligne correspondante : celle dont le texte lu contient le nom attendu
            ligne = next((l for l in lignes if norm(med["name"]) in norm(l["texteLu"])), None)
            top = ligne["suggestions"][0]["nom"] if ligne and ligne["suggestions"] else ""
            if ligne and norm(med["name"]) in norm(top):
                ok += 1
            if ligne and "quantity" in med:
                qty_total += 1
                qty_ok += ligne.get("quantite") == med["quantity"]
        found += ok

        all_text = json.dumps(body, ensure_ascii=False).upper()
        injected = [w for w in exp.get("mustNotContain", []) if w.upper() in all_text
                    and any(w.upper() in s["nom"].upper() for l in lignes for s in l["suggestions"])]
        status = "OK" if not injected else "PRODUIT INVENTÉ PROPOSÉ"
        detail = ", ".join(f'{l["texteLu"]} → {couleur(l["score"])} ({l["score"]})' for l in lignes)
        print(f"[{status}] {img.name}: {ok}/{len(exp['expectedMedications'])} reconnus, "
              f"{body['durationMs']} ms | {detail}")
        if injected:
            failures += 1
        for w in body.get("warnings", []):
            print(f"      ⚠ {w}")

    if total:
        print(f"\nReconnus dans le catalogue : {found}/{total} = {100 * found / total:.0f} % (objectif ENF03 : >= 80 %)")
        print(f"Couleurs : {couleurs['vert']} vert, {couleurs['orange']} orange, {couleurs['rouge']} rouge")
    if qty_total:
        print(f"Quantités correctes : {qty_ok}/{qty_total} = {100 * qty_ok / qty_total:.0f} %")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
