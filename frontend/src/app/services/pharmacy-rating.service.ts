import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface PharmacyRating {
  id: string;
  reservationId: string;
  stars: number;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class PharmacyRatingService {
  private readonly http = inject(HttpClient);
  private url(request: string, reservation: string) {
    return `/api/availability-requests/${encodeURIComponent(request)}/reservations/${encodeURIComponent(reservation)}/rating`;
  }
  private headers() {
    return { Authorization: `Bearer ${sessionStorage.getItem('access_token') ?? ''}` };
  }
  get(request: string, reservation: string) {
    return this.http.get<PharmacyRating | null>(this.url(request, reservation), { headers: this.headers() });
  }
  create(request: string, reservation: string, stars: number) {
    return this.http.post<PharmacyRating>(this.url(request, reservation), { stars }, { headers: this.headers() });
  }
}
