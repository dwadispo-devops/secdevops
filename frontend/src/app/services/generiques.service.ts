import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/** US16 – Format de GET /api/medications/{code}/generiques (medication-service). */
export interface ProduitReference {
  code: string; nom: string; dci: string | null; dosage: string | null; forme: string | null;
  presentation: string | null; ppv: number | null; prixUnitaire: number | null;
  tauxRemboursement: number | null; princepsGenerique: string | null;
}

export interface GeneriqueMoinsCher {
  code: string; nom: string; dosage: string | null; forme: string | null; presentation: string | null;
  ppv: number; prixUnitaire: number | null; economiePourcent: number;
  comparaison: 'PAR_UNITE' | 'PAR_BOITE'; tauxRemboursement: number | null; moinsRembourse: boolean;
}

export interface ReponseGeneriques {
  reference: ProduitReference;
  generiques: GeneriqueMoinsCher[];
  message: string | null;
  source: string;
}

/** Seuls les vrais codes CNOPS (EAN-13) peuvent être comparés. */
export function estCodeCatalogue(code: string | null | undefined): boolean {
  return !!code && /^[0-9]{13}$/.test(code);
}

@Injectable({ providedIn: 'root' })
export class GeneriquesService {
  private readonly http = inject(HttpClient);

  generiques(code: string, limit = 5): Observable<ReponseGeneriques> {
    return this.http.get<ReponseGeneriques>(`/api/medications/${encodeURIComponent(code)}/generiques`, {
      params: { limit },
    });
  }
}
