import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';

/** US15 (amélioration) : une période de garde déclarée par le pharmacien (bornes incluses). */
export interface MyDuty {
  id: string;
  pharmacyId: string;
  pharmacyName: string;
  startsOn: string; // yyyy-MM-dd
  endsOn: string;   // yyyy-MM-dd
}

export interface NewDuty {
  pharmacyId: string;
  startsOn: string;
  endsOn: string;
}

export interface MyPharmacy {
  id: string;
  name: string;
}

@Injectable({ providedIn: 'root' })
export class MyDutyService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/pharmacies/me/gardes';

  private headers() {
    return { Authorization: `Bearer ${sessionStorage.getItem('access_token') ?? ''}` };
  }

  /** Pharmacies du pharmacien connecté (US03). */
  myPharmacies(): Observable<MyPharmacy[]> {
    return this.http
      .get<MyPharmacy[] | MyPharmacy>('/api/pharmacies/me', { headers: this.headers() })
      .pipe(map((r) => (Array.isArray(r) ? r : r ? [r] : [])));
  }

  list(): Observable<MyDuty[]> {
    return this.http.get<MyDuty[]>(this.url, { headers: this.headers() });
  }

  create(duty: NewDuty): Observable<MyDuty> {
    return this.http.post<MyDuty>(this.url, duty, { headers: this.headers() });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${encodeURIComponent(id)}`, { headers: this.headers() });
  }
}
