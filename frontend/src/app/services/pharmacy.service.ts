
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Pharmacy {
  id: string;
  name: string;
  address: string | null;
  phone: string | null;
  openingHours: string | null;
  latitude: number;
  longitude: number;
  distanceM: number;
}

@Injectable({
  providedIn: 'root',
})
export class PharmacyService {
  private readonly http = inject(HttpClient);

  private readonly apiUrl = '/api/pharmacies/nearby';

  getNearbyPharmacies(
    latitude: number,
    longitude: number,
    radius = 5000
  ): Observable<Pharmacy[]> {
    const params = new HttpParams()
      .set('lat', latitude)
      .set('lng', longitude)
      .set('radius', radius);

    return this.http.get<Pharmacy[]>(this.apiUrl, { params });
  }
}
