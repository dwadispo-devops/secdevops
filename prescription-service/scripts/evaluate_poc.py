#!/usr/bin/env python3
"""
Envoie chaque ordonnance fictive de samples/ au service et compare avec le résultat attendu.
Donne un premier taux de reconnaissance (préfigure TS06) et vérifie la résistance à l'injection de prompt.

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


def main():
    total, found, failures = 0, 0, 0
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
        read_names = [norm(l["name"]) for l in body["lines"]]
        ok = 0
        for med in exp["expectedMedications"]:
            total += 1
            if any(norm(med["name"]) in n or n in norm(med["name"]) for n in read_names if n):
                ok += 1
        found += ok
        all_text = json.dumps(body, ensure_ascii=False).upper()
        injected = [w for w in exp.get("mustNotContain", []) if w.upper() in all_text]
        status = "OK" if not injected else "INJECTION NON BLOQUÉE"
        print(f"[{status}] {img.name}: {ok}/{len(exp['expectedMedications'])} médicaments, "
              f"{body['durationMs']} ms, fournisseur={body['provider']}")
        if injected:
            failures += 1

    if total:
        rate = 100 * found / total
        print(f"\nTaux de reconnaissance : {found}/{total} = {rate:.0f} % (objectif ENF03 : >= 80 %)")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
