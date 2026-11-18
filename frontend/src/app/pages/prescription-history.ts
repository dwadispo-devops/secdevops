import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { PrescriptionHistoryItem, PrescriptionService } from '../services/prescription.service';
import { OrdonnanceService } from '../services/ordonnance';

@Component({
  selector: 'app-prescription-history',
  standalone: true,
  imports: [CommonModule],
  template: `
    <main class="container">
      <h1>Mes ordonnances</h1>
      <p>Retrouvez les ordonnances enregistrées sur votre compte.</p>

      @if (loading()) {
        <p role="status">Chargement de vos ordonnances…</p>
      }

      @if (error()) {
        <p class="error" role="alert">{{ error() }}</p>
      }

      @if (!loading() && items().length === 0 && !error()) {
        <p>Aucune ordonnance enregistrée.</p>
        <a href="/scan">Scanner une ordonnance</a>
      }

      @for (item of items(); track item.id) {
        <article class="prescription">
          <div>
            <h2>Ordonnance du {{ item.createdAt | date:'dd/MM/yyyy à HH:mm' }}</h2>
            <p>Format : {{ item.fileType }} · Statut : {{ item.status }}</p>
            <p class="reference">Référence : {{ item.id }}</p>
            @if (!item.imageAvailable) {
              <p class="warning">Image indisponible : cette ordonnance ne peut plus être relancée.</p>
            }
          </div>
          <button type="button"
                  [disabled]="!item.imageAvailable || relaunching() !== null"
                  (click)="relaunch(item)">
            {{ relaunching() === item.id ? 'Préparation…' : 'Relancer une demande' }}
          </button>
        </article>
      }

      <a href="/scan">＋ Ajouter une ordonnance</a>
    </main>
  `,
  styles: [`
    .container { max-width: 850px; margin: 40px auto; padding: 24px; }
    h1 { color: var(--couleur-bandeau); }
    .prescription { display: flex; justify-content: space-between; align-items: center;
      gap: 16px; flex-wrap: wrap; margin: 16px 0; padding: 18px;
      border: 1px solid var(--couleur-bordure); border-radius: 8px; }
    h2 { margin: 0 0 8px; font-size: 1.1rem; }
    p { margin: 5px 0; }
    .reference { color: #555; font-size: .85rem; overflow-wrap: anywhere; }
    .warning, .error { color: #b00020; }
    button { padding: 12px 16px; background: var(--couleur-bandeau); color: white;
      border: 0; border-radius: 6px; cursor: pointer; }
    button:disabled { opacity: .5; cursor: not-allowed; }
  `],
})
export class PrescriptionHistoryPage implements OnInit {
  private readonly prescriptions = inject(PrescriptionService);
  private readonly ordonnance = inject(OrdonnanceService);
  private readonly router = inject(Router);

  readonly items = signal<PrescriptionHistoryItem[]>([]);
  readonly loading = signal(false);
  readonly relaunching = signal<string | null>(null);
  readonly error = signal('');

  ngOnInit(): void {
    const token = sessionStorage.getItem('access_token');
    if (!token) {
      this.error.set('Connectez-vous avec votre compte patient pour consulter vos ordonnances.');
      return;
    }

    this.loading.set(true);
    this.prescriptions.history(token).subscribe({
      next: items => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: response => {
        this.loading.set(false);
        this.error.set(response.status === 401
          ? 'Session expirée. Reconnectez-vous.'
          : 'Impossible de charger vos ordonnances.');
      },
    });
  }

  relaunch(item: PrescriptionHistoryItem): void {
    const token = sessionStorage.getItem('access_token');
    if (!token || this.relaunching()) return;

    this.relaunching.set(item.id);
    this.error.set('');

    this.prescriptions.readStored(item.id, token).subscribe({
      next: lecture => {
        this.relaunching.set(null);
        if (!lecture?.prescriptionDetected || !lecture.lignes?.length) {
          this.error.set('Aucun médicament n’a pu être relu. Réessayez ou scannez une nouvelle ordonnance.');
          return;
        }

        sessionStorage.setItem('last_prescription_id', item.id);
        this.ordonnance.charger(lecture.lignes);
        this.router.navigateByUrl('/verification').catch(() => undefined);
      },
      error: () => {
        this.relaunching.set(null);
        this.error.set('Impossible de relire cette ordonnance. Vérifiez votre session ou réessayez.');
      },
    });
  }
}
