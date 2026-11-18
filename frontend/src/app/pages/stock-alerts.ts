import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

import { CatalogueService } from '../services/catalogue.service';
import { Produit } from '../services/ordonnance';
import { StockAlert, StockAlertService } from '../services/stock-alert.service';

/** US22 / EF25 : le patient est alerté quand un médicament introuvable revient en stock près de lui. */
@Component({
  selector: 'app-stock-alerts',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <main class="alerts">
      <h1>Mes alertes de disponibilité</h1>
      <p>
        Un médicament est introuvable ? Créez une alerte : vous recevrez une notification dès qu’une pharmacie
        proche de vous le remet en stock. Vous pouvez aussi consulter les <a routerLink="/garde">pharmacies de garde</a>.
      </p>

      @if (error()) { <p role="alert" class="error">{{ error() }}</p> }
      @if (success()) { <p role="status" class="success">{{ success() }}</p> }

      <section class="new" aria-label="Nouvelle alerte">
        <h2>Nouvelle alerte</h2>
        <form (ngSubmit)="search()">
          <label>
            Médicament
            <input name="terme" [(ngModel)]="terme" placeholder="ex. Doliprane" autocomplete="off" />
          </label>
          <button type="submit">Rechercher</button>
        </form>

        @if (results().length > 0) {
          <ul class="results">
            @for (p of results(); track p.id) {
              <li>
                <span>{{ p.nom }}</span>
                <button type="button" (click)="choose(p)">Choisir</button>
              </li>
            }
          </ul>
        }
        @if (searched() && results().length === 0) { <p>Aucun médicament trouvé dans le catalogue.</p> }

        @if (selected(); as p) {
          <div class="selection">
            <p>Médicament choisi : <strong>{{ p.nom }}</strong></p>
            <label>
              Rayon
              <select name="rayon" [(ngModel)]="radiusM">
                @for (r of rayons; track r) { <option [ngValue]="r">{{ km(r) }}</option> }
              </select>
            </label>
            <button type="button" (click)="create()" [disabled]="sending()">
              {{ sending() ? 'Création…' : 'M’alerter quand il revient' }}
            </button>
            <p class="hint">Votre position est utilisée uniquement pour trouver les pharmacies proches.</p>
          </div>
        }
      </section>

      <section aria-label="Mes alertes">
        <h2>Mes alertes</h2>
        @if (loading()) { <p role="status">Chargement…</p> }
        @if (!loading() && alerts().length === 0 && !error()) { <p>Aucune alerte pour le moment.</p> }
        @for (a of alerts(); track a.id) {
          <article class="alert" [class.done]="a.status === 'DECLENCHEE'">
            <div>
              <strong>{{ a.medicationName }}</strong>
              @if (a.status === 'ACTIVE') {
                <span class="badge">En attente · {{ km(a.radiusM) }}</span>
              } @else {
                <span class="badge ok">Disponible</span>
                <span>chez {{ a.pharmacyName ?? 'une pharmacie proche' }}@if (a.pharmacyAddress) { – {{ a.pharmacyAddress }} }</span>
                <span class="date">le {{ format(a.triggeredAt) }}</span>
              }
            </div>
            <button type="button" class="secondaire" (click)="remove(a)">Supprimer</button>
          </article>
        }
      </section>
    </main>
  `,
  styles: [`
    .alerts { max-width: 760px; margin: 0 auto; padding: 1.5rem 1rem; }
    .new, .selection { border: 1px solid #d8e2dc; border-radius: 8px; padding: 1rem; margin: 1rem 0; }
    form, .selection { display: flex; flex-wrap: wrap; gap: .75rem; align-items: flex-end; }
    label { display: flex; flex-direction: column; gap: .25rem; }
    .results { list-style: none; padding: 0; }
    .results li { display: flex; justify-content: space-between; gap: 1rem; padding: .4rem 0; border-bottom: 1px solid #eee; }
    .alert { display: flex; justify-content: space-between; gap: 1rem; align-items: center;
             border: 1px solid #d8e2dc; border-radius: 8px; padding: .75rem 1rem; margin: .5rem 0; }
    .alert div { display: flex; flex-direction: column; gap: .2rem; }
    .alert.done { background: #eef8f1; }
    .badge { font-size: .85rem; color: #555; }
    .badge.ok { color: #1b7a3a; font-weight: 600; }
    .hint, .date { font-size: .85rem; color: #666; }
    .error { color: #b00020; } .success { color: #1b7a3a; }
  `],
})
export class StockAlertsPage implements OnInit {
  private readonly service = inject(StockAlertService);
  private readonly catalogue = inject(CatalogueService);

  readonly rayons = [2000, 5000, 10000, 20000];
  readonly alerts = signal<StockAlert[]>([]);
  readonly results = signal<Produit[]>([]);
  readonly selected = signal<Produit | null>(null);
  readonly loading = signal(true);
  readonly searched = signal(false);
  readonly sending = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  terme = '';
  radiusM = 5000;

  ngOnInit(): void {
    this.service.list().subscribe({
      next: (liste) => { this.alerts.set(liste ?? []); this.loading.set(false); },
      error: (e) => { this.loading.set(false); this.error.set(this.message(e)); },
    });
  }

  search(): void {
    this.error.set('');
    this.selected.set(null);
    this.catalogue.rechercher(this.terme).subscribe((r) => { this.results.set(r); this.searched.set(true); });
  }

  choose(p: Produit): void {
    this.selected.set(p);
    this.results.set([]);
    this.searched.set(false);
    this.success.set('');
  }

  async create(): Promise<void> {
    const p = this.selected();
    if (!p) return;
    this.error.set('');
    this.success.set('');
    this.sending.set(true);
    let pos: { latitude: number; longitude: number };
    try {
      pos = await this.position();
    } catch {
      this.sending.set(false);
      this.error.set('Impossible d’obtenir votre position : autorisez la localisation dans votre navigateur.');
      return;
    }
    this.service.create({
      medicationCode: p.id,
      medicationName: p.nom.slice(0, 200),
      latitude: pos.latitude,
      longitude: pos.longitude,
      radiusM: this.radiusM,
    }).subscribe({
      next: (a) => {
        this.alerts.update((l) => [a, ...l]);
        this.selected.set(null);
        this.terme = '';
        this.sending.set(false);
        this.success.set('Alerte créée : vous serez prévenu(e) dès qu’une pharmacie proche le remet en stock.');
      },
      error: (e) => { this.sending.set(false); this.error.set(this.message(e)); },
    });
  }

  remove(a: StockAlert): void {
    this.error.set('');
    this.success.set('');
    this.service.delete(a.id).subscribe({
      next: () => this.alerts.update((l) => l.filter((x) => x.id !== a.id)),
      error: (e) => this.error.set(this.message(e)),
    });
  }

  km(metres: number): string {
    return `${metres / 1000} km`;
  }

  format(iso: string | null): string {
    if (!iso) return '';
    return new Date(iso).toLocaleString('fr-FR', { timeZone: 'Africa/Casablanca', dateStyle: 'short', timeStyle: 'short' });
  }

  private position(): Promise<{ latitude: number; longitude: number }> {
    return new Promise((resolve, reject) => {
      if (!('geolocation' in navigator)) { reject(new Error('géolocalisation indisponible')); return; }
      navigator.geolocation.getCurrentPosition(
        (p) => resolve({ latitude: p.coords.latitude, longitude: p.coords.longitude }),
        reject,
        { timeout: 10000, maximumAge: 300000 },
      );
    });
  }

  private message(e: unknown): string {
    const s = e instanceof HttpErrorResponse ? e.status : 0;
    if (s === 401 || s === 403) return 'Connectez-vous avec votre compte patient pour gérer vos alertes.';
    if (s === 409) return 'Vous avez déjà une alerte active pour ce médicament.';
    if (s === 400) return 'Alerte refusée : vous avez peut-être déjà 10 alertes actives.';
    if (s === 404) return 'Cette alerte n’existe plus.';
    return 'Le service est momentanément indisponible. Réessayez plus tard.';
  }
}
