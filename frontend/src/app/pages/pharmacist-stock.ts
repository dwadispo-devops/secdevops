import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

import { CatalogueService } from '../services/catalogue.service';
import { MyDutyService, MyPharmacy } from '../services/my-duty.service';
import { Produit } from '../services/ordonnance';
import { PharmacyStockService, StockStatus } from '../services/pharmacy-stock.service';

interface MiseAJour { nom: string; status: StockStatus; at: string; }

/** US11 / US22 : le pharmacien déclare un médicament en stock ou en rupture. */
@Component({
  selector: 'app-pharmacist-stock',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <main class="stock">
      <h1>Mon stock</h1>
      <p>
        Indiquez si un médicament est disponible dans votre pharmacie. Quand vous le remettez
        <strong>en stock</strong>, les patients qui l’attendent près de chez vous sont prévenus automatiquement.
      </p>
      <p><a routerLink="/pharmacien/demandes">← Demandes reçues</a></p>

      @if (error()) { <p role="alert" class="error">{{ error() }}</p> }
      @if (success()) { <p role="status" class="success">{{ success() }}</p> }
      @if (loading()) { <p role="status">Chargement…</p> }

      @if (!loading() && pharmacies().length === 0 && !error()) {
        <p>Aucune pharmacie n’est rattachée à votre compte.</p>
      }

      @if (pharmacies().length > 0) {
        <form (ngSubmit)="search()">
          <label>
            Pharmacie
            <select name="pharmacyId" [(ngModel)]="pharmacyId">
              @for (p of pharmacies(); track p.id) { <option [value]="p.id">{{ p.name }}</option> }
            </select>
          </label>
          <label>
            Médicament
            <input name="terme" [(ngModel)]="terme" placeholder="ex. Doliprane" autocomplete="off" />
          </label>
          <button type="submit">Rechercher</button>
        </form>

        @if (searched() && results().length === 0) { <p>Aucun médicament trouvé dans le catalogue.</p> }
        <ul class="results">
          @for (p of results(); track p.id) {
            <li>
              <span>{{ p.nom }}</span>
              <span class="actions">
                <button type="button" (click)="update(p, 'AVAILABLE')" [disabled]="sending() === p.id">En stock</button>
                <button type="button" class="secondaire" (click)="update(p, 'OUT_OF_STOCK')" [disabled]="sending() === p.id">En rupture</button>
              </span>
            </li>
          }
        </ul>
      }

      @if (history().length > 0) {
        <section aria-label="Dernières mises à jour">
          <h2>Dernières mises à jour</h2>
          <ul>
            @for (h of history(); track h.at) {
              <li>{{ h.nom }} : <strong>{{ libelle(h.status) }}</strong></li>
            }
          </ul>
        </section>
      }
    </main>
  `,
  styles: [`
    .stock { max-width: 760px; margin: 0 auto; padding: 1.5rem 1rem; }
    form { display: flex; flex-wrap: wrap; gap: .75rem; align-items: flex-end; margin: 1rem 0; }
    label { display: flex; flex-direction: column; gap: .25rem; }
    .results { list-style: none; padding: 0; }
    .results li { display: flex; justify-content: space-between; gap: 1rem; align-items: center;
                  padding: .5rem 0; border-bottom: 1px solid #eee; }
    .actions { display: flex; gap: .5rem; }
    .error { color: #b00020; } .success { color: #1b7a3a; }
  `],
})
export class PharmacistStockPage implements OnInit {
  private readonly mesPharmacies = inject(MyDutyService);
  private readonly stock = inject(PharmacyStockService);
  private readonly catalogue = inject(CatalogueService);

  readonly pharmacies = signal<MyPharmacy[]>([]);
  readonly results = signal<Produit[]>([]);
  readonly history = signal<MiseAJour[]>([]);
  readonly loading = signal(true);
  readonly searched = signal(false);
  readonly sending = signal('');
  readonly error = signal('');
  readonly success = signal('');
  pharmacyId = '';
  terme = '';

  ngOnInit(): void {
    this.mesPharmacies.myPharmacies().subscribe({
      next: (liste) => {
        this.pharmacies.set(liste);
        this.pharmacyId = liste[0]?.id ?? '';
        this.loading.set(false);
      },
      error: (e) => { this.loading.set(false); this.error.set(this.message(e)); },
    });
  }

  search(): void {
    this.error.set('');
    this.catalogue.rechercher(this.terme).subscribe((r) => { this.results.set(r); this.searched.set(true); });
  }

  update(p: Produit, status: StockStatus): void {
    if (!this.pharmacyId) return;
    this.error.set('');
    this.success.set('');
    this.sending.set(p.id);
    this.stock.update(this.pharmacyId, p.id, status).subscribe({
      next: () => {
        this.sending.set('');
        this.success.set(`« ${p.nom} » est maintenant ${this.libelle(status)}.`);
        this.history.update((h) => [{ nom: p.nom, status, at: new Date().toISOString() + Math.random() }, ...h].slice(0, 10));
      },
      error: (e) => { this.sending.set(''); this.error.set(this.message(e)); },
    });
  }

  libelle(status: StockStatus): string {
    return status === 'AVAILABLE' ? 'en stock' : 'en rupture';
  }

  private message(e: unknown): string {
    const s = e instanceof HttpErrorResponse ? e.status : 0;
    if (s === 401 || s === 403) return 'Connectez-vous avec le compte pharmacien de cette pharmacie.';
    if (s === 404) return 'Pharmacie introuvable.';
    if (s === 400) return 'Mise à jour refusée : données invalides.';
    return 'Le service est momentanément indisponible. Réessayez plus tard.';
  }
}
