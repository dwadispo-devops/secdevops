import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface EstimateItem { code: string; quantity: number; unitConfirmed: boolean; }
export interface EstimateLine {
  code: string; name: string | null; presentation: string | null; quantity: number;
  unitPrice: number | null; catalogRate: number | null; estimatedCost: number | null; reason: string | null;
}
export interface PrescriptionEstimate {
  currency: string; source: string; calculatedAt: string; lines: EstimateLine[]; complete: boolean;
  knownSubtotal: number; totalEstimatedCost: number | null; reimbursableAmount: number | null;
  remainingCharge: number | null; reimbursementExplanation: string;
}

@Injectable({ providedIn: 'root' })
export class PrescriptionEstimateService {
  private readonly http = inject(HttpClient);
  estimate(items: EstimateItem[]) {
    return this.http.post<PrescriptionEstimate>('/api/v1/prescriptions/estimate', {
      prescriptionId: sessionStorage.getItem('last_prescription_id') || null, items,
    }, { headers: { Authorization: `Bearer ${sessionStorage.getItem('access_token') ?? ''}` } });
  }
}
