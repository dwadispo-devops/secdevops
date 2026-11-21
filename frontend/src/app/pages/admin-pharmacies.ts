import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { AdminPharmaciesService, PendingPharmacy } from '../services/admin-pharmacies.service';

@Component({
  selector: 'app-admin-pharmacies',
  standalone: true,
  imports: [DatePipe],
  template: `
    <main class="admin-page">
      <header>
        <div><span class="eyebrow">ADMINISTRATION</span><h1>Pharmacies à valider</h1></div>
        <button type="button" (click)="reload()" [disabled]="loading() || validatingId() !== null">
          {{ loading() ? 'Chargement…' : 'Actualiser' }}
        </button>
      </header>
      @if (error()) { <p role="alert" class="error">{{ error() }}</p> }
      @if (loading() && pharmacies().length === 0) { <p role="status">Chargement des pharmacies…</p> }
      @if (!loading() && pharmacies().length === 0 && !error()) {
        <p class="empty" role="status">Aucune pharmacie en attente</p>
      }
      @if (pharmacies().length > 0) {
        <ul>
          @for (pharmacy of pharmacies(); track pharmacy.id) {
            <li>
              <div class="details">
                <h2>{{ pharmacy.name }}</h2>
                <p>{{ pharmacy.address || 'Adresse non renseignée' }}</p>
                <p><strong>N° d’autorisation :</strong> {{ pharmacy.licenseNumber }}</p>
                <p><strong>Inscrite le :</strong> {{ pharmacy.createdAt | date:'medium' }}</p>
              </div>
              <button type="button" [disabled]="validatingId() !== null"
                      (click)="validate(pharmacy)">
                {{ validatingId() === pharmacy.id ? 'Validation…' : 'Valider' }}
              </button>
            </li>
          }
        </ul>
      }
    </main>
  `,
  styles: [`
    .admin-page { max-width: 900px; margin: 2rem auto; padding: 1rem; }
    header, li { display: flex; align-items: center; justify-content: space-between; gap: 1rem; }
    .eyebrow { color: var(--couleur-or-texte); font-size: .8rem; font-weight: 700; }
    h1 { color: var(--couleur-bandeau); }
    ul { list-style: none; padding: 0; }
    li { margin: .8rem 0; padding: 1rem; border: 1px solid #ddd; border-radius: 8px; }
    h2 { margin: 0 0 .4rem; font-size: 1.1rem; }
    p { margin: .25rem 0; }
    button { padding: .65rem 1rem; font: inherit; cursor: pointer; }
    button:disabled { cursor: default; opacity: .6; }
    .empty { padding: 2rem; text-align: center; }
    .error { color: #a11525; }
    @media (max-width: 600px) { li { align-items: flex-start; flex-direction: column; } }
  `],
})
export class AdminPharmaciesPage implements OnInit {
  private readonly api = inject(AdminPharmaciesService);
  readonly pharmacies = signal<PendingPharmacy[]>([]);
  readonly loading = signal(false);
  readonly validatingId = signal<string | null>(null);
  readonly error = signal('');

  ngOnInit(): void { this.reload(); }

  reload(): void {
    this.error.set('');
    this.loading.set(true);
    this.api.listPending().subscribe({
      next: pharmacies => { this.pharmacies.set(pharmacies); this.loading.set(false); },
      error: error => { this.error.set(this.message(error)); this.loading.set(false); },
    });
  }

  validate(pharmacy: PendingPharmacy): void {
    if (this.validatingId()) return;
    this.error.set('');
    this.validatingId.set(pharmacy.id);
    this.api.validate(pharmacy.id).subscribe({
      next: () => {
        this.pharmacies.update(items => items.filter(item => item.id !== pharmacy.id));
        this.validatingId.set(null);
      },
      error: error => {
        this.error.set(this.message(error));
        this.validatingId.set(null);
      },
    });
  }

  private message(error: unknown): string {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    return ({
      401: 'Votre session a expiré. Reconnectez-vous.',
      403: 'Cette action est réservée à un administrateur.',
      404: 'Cette pharmacie est introuvable.',
      409: 'Cette pharmacie a déjà été traitée. Actualisez la liste.',
    } as Record<number, string>)[status] ?? 'Impossible de joindre le serveur. Réessayez.';
  }
}
