import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface RecentReservation {
  id: string;
  requestId: string;
  pharmacyName: string;
  status: string;
  price: number;
  createdAt: string;
  expiresAt: string;
}

export interface PharmacistDashboard {
  totalRequests: number;
  pendingRequests: number;
  respondedRequests: number;
  responseRate: number;
  totalReservations: number;
  activeReservations: number;
  pickedUpReservations: number;
  recentReservations: RecentReservation[];
}

@Injectable({ providedIn: 'root' })
export class PharmacistDashboardService {
  private readonly http = inject(HttpClient);

  load() {
    const headers = {
      Authorization: `Bearer ${sessionStorage.getItem('access_token') ?? ''}`
    };

    return this.http.get<PharmacistDashboard>(
      '/api/pharmacies/me/dashboard',
      { headers }
    );
  }
}
