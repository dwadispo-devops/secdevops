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

export interface DutyPharmacy {
  id: string;
  name: string;
  address: string | null;
  phone: string | null;
  openingHours: string | null;
  latitude: number;
  longitude: number;
  distanceM: number | null;
  dutyUntil: string; // yyyy-MM-dd
}

export interface DutyResponse {
  date: string; // yyyy-MM-dd, « aujourd'hui » à l'heure du Maroc
  pharmacies: DutyPharmacy[];
}

@Injectable({
  providedIn: 'root',
})
export class PharmacyService {
  private readonly http = inject(HttpClient);

  private readonly apiUrl = '/api/pharmacies/nearby';
  private readonly dutyUrl = '/api/pharmacies/garde';

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

  /** US15 : pharmacies de garde du jour (triées par distance si la position est fournie). */
  getOnDutyPharmacies(latitude?: number, longitude?: number): Observable<DutyResponse> {
    let params = new HttpParams();
    if (latitude !== undefined && longitude !== undefined) {
      params = params.set('lat', latitude).set('lng', longitude);
    }
    return this.http.get<DutyResponse>(this.dutyUrl, { params });
  }
}
