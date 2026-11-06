import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface PharmacyRequest {
  requestId: string; pharmacyId: string; pharmacyName: string; status: string;
  createdAt: string; prescriptionId: string | null; price: number | null;
  comment: string | null; respondedAt: string | null;
}
export interface RequestDetail {
  request: PharmacyRequest;
  medications: { name: string; quantity: number }[];
}
export interface PrescriptionView {
  imageAvailable: boolean; mediaType: string | null; ocrText: string | null;
  medications: { rawText: string; name: string | null; dosage: string | null; status: string }[];
}
export interface PharmacistResponse {
  availability: 'AVAILABLE' | 'UNAVAILABLE'; price: number | null; comment: string | null;
}

@Injectable({ providedIn: 'root' })
export class PharmacistReviewService {
  private readonly http = inject(HttpClient);
  private headers() { return { Authorization: `Bearer ${sessionStorage.getItem('access_token') ?? ''}` }; }
  private url(r: PharmacyRequest) {
    return `/api/pharmacies/${encodeURIComponent(r.pharmacyId)}/requests/${encodeURIComponent(r.requestId)}`;
  }
  list() { return this.http.get<PharmacyRequest[]>('/api/pharmacies/me/requests', { headers: this.headers() }); }
  detail(r: PharmacyRequest) { return this.http.get<RequestDetail>(this.url(r), { headers: this.headers() }); }
  prescription(r: PharmacyRequest) {
    return this.http.get<PrescriptionView>(`${this.url(r)}/prescription`, { headers: this.headers() });
  }
  image(r: PharmacyRequest) {
    return this.http.get(`${this.url(r)}/prescription/image`, { headers: this.headers(), responseType: 'blob' });
  }
  respond(r: PharmacyRequest, body: PharmacistResponse) {
    return this.http.post<PharmacyRequest>(`${this.url(r)}/response`, body, { headers: this.headers() });
  }
}
