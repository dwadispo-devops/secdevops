import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type StockStatus = 'AVAILABLE' | 'OUT_OF_STOCK';

/** US11 / US22 : le pharmacien déclare la disponibilité d'un médicament dans sa pharmacie. */
@Injectable({ providedIn: 'root' })
export class PharmacyStockService {
  private readonly http = inject(HttpClient);

  private headers() {
    return { Authorization: `Bearer ${sessionStorage.getItem('access_token') ?? ''}` };
  }

  update(pharmacyId: string, medicationCode: string, status: StockStatus): Observable<void> {
    return this.http.put<void>(
      `/api/pharmacies/${encodeURIComponent(pharmacyId)}/stock/${encodeURIComponent(medicationCode)}`,
      { status },
      { headers: this.headers() },
    );
  }
}
