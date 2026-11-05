import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { PharmacistReviewService, PharmacyRequest, RequestDetail, PrescriptionView } from '../services/pharmacist-review.service';

@Component({
  selector: 'app-pharmacist-requests',
  imports: [FormsModule, DatePipe, DecimalPipe, RouterLink],
  templateUrl: './pharmacist-requests.html',
  styleUrl: './pharmacist-requests.scss',
})
export class PharmacistRequestsPage implements OnInit, OnDestroy {
  private readonly api = inject(PharmacistReviewService);
  private loads = new Subscription();
  private readonly lifetime = new Subscription();
  readonly requests = signal<PharmacyRequest[]>([]);
  readonly detail = signal<RequestDetail | null>(null);
  readonly prescription = signal<PrescriptionView | null>(null);
  readonly imageUrl = signal<string | null>(null);
  readonly imageError = signal('');
  readonly prescriptionError = signal('');
  readonly error = signal('');
  readonly success = signal('');
  readonly loading = signal(false);
  readonly detailLoading = signal(false);
  readonly sending = signal(false);
  availability: 'AVAILABLE' | 'UNAVAILABLE' = 'AVAILABLE';
  price = '';
  comment = '';

  ngOnInit() { this.reload(); }
  reload() {
    if (this.sending()) return;
    this.error.set('');
    if (!sessionStorage.getItem('access_token')) {
      this.error.set('Connectez-vous avec un compte pharmacien.'); return;
    }
    this.loading.set(true);
    this.lifetime.add(this.api.list().subscribe({
      next: r => { this.requests.set(r); this.loading.set(false); },
      error: e => { this.error.set(this.message(e.status)); this.loading.set(false); },
    }));
  }
  select(r: PharmacyRequest) {
    if (this.sending()) return;
    this.loads.unsubscribe(); this.loads = new Subscription();
    this.revokeImage(); this.detail.set(null); this.prescription.set(null);
    this.error.set(''); this.success.set(''); this.imageError.set(''); this.prescriptionError.set('');
    this.detailLoading.set(true); this.availability = 'AVAILABLE'; this.price = ''; this.comment = '';
    this.loads.add(this.api.detail(r).subscribe({
      next: d => { this.detail.set(d); this.detailLoading.set(false); },
      error: e => { this.error.set(this.message(e.status)); this.detailLoading.set(false); },
    }));
    this.loads.add(this.api.prescription(r).subscribe({
      next: p => {
        this.prescription.set(p);
        if (p.imageAvailable) this.loads.add(this.api.image(r).subscribe({
          next: blob => this.imageUrl.set(URL.createObjectURL(blob)),
          error: () => this.imageError.set('Image indisponible ou supprimée.'),
        }));
      },
      error: e => this.prescriptionError.set(e.status === 503
        ? 'Service ordonnance indisponible. Réessayez plus tard.' : 'Ordonnance indisponible.'),
    }));
  }
  valid() {
    return this.comment.length <= 1000 && (this.availability === 'UNAVAILABLE'
      || (/^\d{1,10}(\.\d{1,2})?$/.test(this.price) && Number.isFinite(Number(this.price))));
  }
  submit() {
    const d = this.detail();
    if (!d || d.request.status !== 'PENDING' || this.sending()) return;
    this.error.set(''); this.success.set('');
    if (!this.valid()) { this.error.set('Saisissez un prix positif ou nul avec deux décimales maximum et un commentaire de 1000 caractères maximum.'); return; }
    this.sending.set(true);
    this.lifetime.add(this.api.respond(d.request, {
      availability: this.availability,
      price: this.availability === 'AVAILABLE' ? Number(this.price) : null,
      comment: this.comment.trim() || null,
    }).subscribe({
      next: r => {
        this.detail.set({ ...d, request: r });
        this.requests.update(all => all.map(x => x.requestId === r.requestId && x.pharmacyId === r.pharmacyId ? r : x));
        this.sending.set(false); this.success.set('Votre réponse a été enregistrée et peut être consultée par le patient.');
      },
      error: e => { this.sending.set(false); this.error.set(this.message(e.status)); },
    }));
  }
  label(status: string) {
    return ({ PENDING: 'En attente', AVAILABLE: 'Disponible', UNAVAILABLE: 'Indisponible' } as Record<string,string>)[status] ?? status;
  }
  private message(status: number) {
    return ({ 401: 'Session invalide ou expirée. Reconnectez-vous.', 403: 'Accès réservé au pharmacien autorisé.',
      404: 'Demande introuvable ou non attribuée à votre pharmacie.',
      409: 'Cette demande a déjà reçu une réponse. Actualisez la liste.', 400: 'Réponse invalide. Vérifiez le prix et le commentaire.' } as Record<number,string>)[status]
      ?? 'Impossible de joindre le serveur. Réessayez.';
  }
  private revokeImage() { const url = this.imageUrl(); if (url) URL.revokeObjectURL(url); this.imageUrl.set(null); }
  ngOnDestroy() { this.loads.unsubscribe(); this.lifetime.unsubscribe(); this.revokeImage(); }
}
