import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError, map, of } from 'rxjs';
import { Produit } from './ordonnance';

/** Format renvoyé par GET /api/medications/search (medication-service). */
interface MedicamentCatalogue {
  code: string; nom: string; dosageBrut?: string | null; uniteDosage1?: string | null;
  forme?: string | null; presentation?: string | null;
}

/** Même libellé que les suggestions du RAG : « NOM DOSAGE, FORME, PRESENTATION ». */
export function versProduit(m: MedicamentCatalogue): Produit {
  const dosage = [m.dosageBrut, m.uniteDosage1].filter(Boolean).join(' ');
  return { id: m.code, nom: [[m.nom, dosage].filter(Boolean).join(' '), m.forme, m.presentation].filter(Boolean).join(', ') };
}

/** RG02 : la correction d'une ligne ne propose que des produits du catalogue officiel CNOPS. */
@Injectable({ providedIn: 'root' })
export class CatalogueService {
  private readonly http = inject(HttpClient);

  rechercher(terme: string): Observable<Produit[]> {
    const t = terme.trim();
    if (t.length < 2) return of([]);
    return this.http.get<MedicamentCatalogue[]>('/api/medications/search', { params: new HttpParams().set('q', t) }).pipe(
      map(liste => (liste ?? []).slice(0, 8).map(versProduit)),
      catchError(() => of([])),
    );
  }
}
