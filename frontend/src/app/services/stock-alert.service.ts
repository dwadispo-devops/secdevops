import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/** US22 : alerte de retour en stock (renvoyée par GET /api/alertes/me). */
export interface StockAlert {
  id: string;
  medicationCode: string;
  medicationName: string;
  radiusM: number;
  status: 'ACTIVE' | 'DECLENCHEE';
  createdAt: string;
  triggeredAt: string | null;
  pharmacyId: string | null;
  pharmacyName: string | null;
  pharmacyAddress: string | null;
}

export interface NewStockAlert {
  medicationCode: string;
  medicationName: string;
  latitude: number;
  longitude: number;
  radiusM: number;
}

@Injectable({ providedIn: 'root' })
export class StockAlertService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/alertes';

  private headers() {
    return { Authorization: `Bearer ${sessionStorage.getItem('access_token') ?? ''}` };
  }

  list(): Observable<StockAlert[]> {
    return this.http.get<StockAlert[]>(`${this.url}/me`, { headers: this.headers() });
  }

  create(alerte: NewStockAlert): Observable<StockAlert> {
    return this.http.post<StockAlert>(this.url, alerte, { headers: this.headers() });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${encodeURIComponent(id)}`, { headers: this.headers() });
  }
}
