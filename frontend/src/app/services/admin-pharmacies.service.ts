import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface PendingPharmacy {
  id: string;
  name: string;
  address: string | null;
  licenseNumber: string;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class AdminPharmaciesService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/pharmacies/admin';

  private headers() {
    return { Authorization: `Bearer ${sessionStorage.getItem('access_token') ?? ''}` };
  }

  listPending(): Observable<PendingPharmacy[]> {
    return this.http.get<PendingPharmacy[]>(`${this.baseUrl}/pending`, { headers: this.headers() });
  }

  validate(id: string): Observable<PendingPharmacy> {
    return this.http.post<PendingPharmacy>(
      `${this.baseUrl}/${encodeURIComponent(id)}/validate`, {}, { headers: this.headers() });
  }
}
