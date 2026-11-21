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
      <header class="page-header">
        <div>
          <span class="eyebrow">ESPACE PHARMACIEN</span>
          <h1>Gestion de mon stock</h1>
          <p>Consultez le catalogue et actualisez la disponibilité de vos médicaments.</p>
        </div>
        <div class="header-icon" aria-hidden="true">▤</div>
      </header>

      <div class="info-banner">
        <span class="info-icon" aria-hidden="true">i</span>
        <p>
          Lorsque vous indiquez qu'un médicament est <strong>en stock</strong>,
          les patients qui l'attendent près de votre pharmacie sont prévenus automatiquement.
        </p>
      </div>

      <nav class="page-nav">
        <a routerLink="/pharmacien/demandes">← Retour aux demandes reçues</a>
      </nav>

      @if (error()) {
        <div role="alert" class="feedback error">
          <span>!</span><p>{{ error() }}</p>
        </div>
      }

      @if (success()) {
        <div role="status" class="feedback success">
          <span>✓</span><p>{{ success() }}</p>
        </div>
      }

      @if (loading()) {
        <div class="empty-state">
          <span class="loader"></span>
          <p>Chargement de vos pharmacies...</p>
        </div>
      }

      @if (!loading() && pharmacies().length === 0 && !error()) {
        <div class="empty-state">
          <span class="empty-icon">▤</span>
          <h2>Aucune pharmacie associée</h2>
          <p>Aucune pharmacie n’est actuellement rattachée à votre compte.</p>
        </div>
      }

      @if (pharmacies().length > 0) {
        <section class="search-card">
          <div class="section-heading">
            <div>
              <span class="eyebrow">CATALOGUE</span>
              <h2>Mettre à jour la disponibilité</h2>
              <p>Sélectionnez votre pharmacie et recherchez un médicament.</p>
            </div>
            <span class="section-icon">⌕</span>
          </div>

          <form (ngSubmit)="search()">
            <label>
              <span>Pharmacie</span>
              <select name="pharmacyId" [(ngModel)]="pharmacyId">
                @for (p of pharmacies(); track p.id) {
                  <option [value]="p.id">{{ p.name }}</option>
                }
              </select>
            </label>

            <label class="medicine-field">
              <span>Nom du médicament</span>
              <input
                name="terme"
                [(ngModel)]="terme"
                placeholder="Ex. Doliprane"
                autocomplete="off"
              />
            </label>

            <button class="search-button" type="submit">
              <span aria-hidden="true">⌕</span> Rechercher
            </button>
          </form>
        </section>

        @if (searched()) {
          <section class="results-section">
            <div class="section-heading results-heading">
              <div>
                <span class="eyebrow">RÉSULTATS</span>
                <h2>Médicaments trouvés</h2>
              </div>
              <span class="result-count">{{ results().length }}</span>
            </div>

            @if (results().length === 0) {
              <div class="empty-state compact">
                <span class="empty-icon">⌕</span>
                <h3>Aucun résultat</h3>
                <p>Aucun médicament trouvé dans le catalogue. Essayez un autre nom.</p>
              </div>
            } @else {
              <ul class="results">
                @for (p of results(); track p.id) {
                  <li class="medicine-row">
                    <div class="medicine-info">
                      <span class="medicine-icon">✚</span>
                      <div>
                        <strong>{{ p.nom }}</strong>
                        <small>Disponibilité du médicament</small>
                      </div>
                    </div>

                    <div class="actions">
                      <button
                        type="button"
                        class="available-button"
                        (click)="update(p, 'AVAILABLE')"
                        [disabled]="sending() === p.id"
                      >
                        @if (sending() === p.id) {
                          Enregistrement...
                        } @else {
                          <span>✓</span> En stock
                        }
                      </button>

                      <button
                        type="button"
                        class="unavailable-button"
                        (click)="update(p, 'OUT_OF_STOCK')"
                        [disabled]="sending() === p.id"
                      >
                        <span>×</span> En rupture
                      </button>
                    </div>
                  </li>
                }
              </ul>
            }
          </section>
        }
      }

      @if (history().length > 0) {
        <section class="history-section">
          <div class="section-heading">
            <div>
              <span class="eyebrow">ACTIVITÉ RÉCENTE</span>
              <h2>Dernières mises à jour</h2>
              <p>Historique des modifications effectuées pendant cette session.</p>
            </div>
            <span class="section-icon">◷</span>
          </div>

          <ul class="history-list">
            @for (h of history(); track h.at) {
              <li>
                <div class="history-medicine">
                  <span class="history-dot" [class.out]="h.status === 'OUT_OF_STOCK'"></span>
                  <strong>{{ h.nom }}</strong>
                </div>
                <span class="status-pill" [class.out]="h.status === 'OUT_OF_STOCK'">
                  {{ libelle(h.status) }}
                </span>
              </li>
            }
          </ul>
        </section>
      }
    </main>
  `,
  styles: [`
    :host { display: block; background: #f5f7f5; min-height: 100%; }

    .stock {
      max-width: 1080px;
      margin: 0 auto;
      padding: 2.5rem 1.5rem 4rem;
      color: #24372e;
    }

    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 1rem;
      margin-bottom: 1.6rem;
    }

    .eyebrow {
      display: block;
      color: #a7833e;
      font-size: .7rem;
      font-weight: 800;
      letter-spacing: .13em;
      margin-bottom: .55rem;
    }

    h1 {
      color: #173c2c;
      font-size: clamp(1.8rem, 4vw, 2.4rem);
      line-height: 1.15;
      margin: 0 0 .55rem;
      font-weight: 800;
    }

    .page-header p, .section-heading p {
      color: #758179;
      margin: 0;
      line-height: 1.6;
    }

    .header-icon {
      width: 58px;
      height: 58px;
      display: grid;
      place-items: center;
      flex-shrink: 0;
      border-radius: 17px;
      background: #e6eee8;
      color: #24563e;
      font-size: 1.8rem;
    }

    .info-banner {
      display: flex;
      align-items: flex-start;
      gap: .85rem;
      padding: 1rem 1.2rem;
      background: #edf3ee;
      border: 1px solid #dce8de;
      border-radius: 12px;
      margin-bottom: 1.2rem;
    }

    .info-icon {
      display: grid;
      place-items: center;
      width: 23px;
      height: 23px;
      flex-shrink: 0;
      border-radius: 50%;
      background: #d6e5d9;
      color: #24563e;
      font-weight: 800;
      font-size: .85rem;
    }

    .info-banner p {
      margin: 0;
      color: #456052;
      line-height: 1.6;
      font-size: .9rem;
    }

    .page-nav { margin: 1rem 0 1.5rem; }

    .page-nav a {
      color: #35674b;
      text-decoration: none;
      font-weight: 700;
      font-size: .88rem;
    }

    .page-nav a:hover { text-decoration: underline; }

    .feedback {
      display: flex;
      align-items: center;
      gap: .75rem;
      padding: .9rem 1rem;
      border-radius: 10px;
      margin: 1rem 0;
      font-size: .9rem;
    }

    .feedback span {
      display: grid;
      place-items: center;
      width: 24px;
      height: 24px;
      flex-shrink: 0;
      border-radius: 50%;
      font-weight: 800;
    }

    .feedback p { margin: 0; }
    .error { background: #fff0ef; color: #a3332d; border: 1px solid #f3d5d2; }
    .error span { background: #f7d9d6; }
    .success { background: #edf6ef; color: #28623e; border: 1px solid #d4e9d8; }
    .success span { background: #d6eadb; }

    .search-card, .results-section, .history-section {
      background: #fff;
      border: 1px solid #e8ece8;
      border-radius: 16px;
      padding: 1.5rem;
      margin-top: 1.3rem;
      box-shadow: 0 3px 14px rgb(25 55 38 / 4%);
    }

    .section-heading {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 1rem;
      margin-bottom: 1.3rem;
    }

    .section-heading h2 {
      color: #1d4030;
      font-size: 1.2rem;
      margin: 0 0 .35rem;
      font-weight: 750;
    }

    .section-heading p { font-size: .85rem; }

    .section-icon {
      display: grid;
      place-items: center;
      width: 43px;
      height: 43px;
      flex-shrink: 0;
      border-radius: 12px;
      background: #f4f0e6;
      color: #98783d;
      font-size: 1.5rem;
    }

    form {
      display: grid;
      grid-template-columns: 1fr 1.3fr auto;
      align-items: end;
      gap: 1rem;
    }

    label { display: flex; flex-direction: column; gap: .5rem; min-width: 0; }

    label > span {
      color: #40554a;
      font-size: .82rem;
      font-weight: 700;
    }

    input, select {
      box-sizing: border-box;
      width: 100%;
      min-height: 45px;
      padding: .7rem .85rem;
      border: 1px solid #dce4dd;
      border-radius: 9px;
      background: #fff;
      color: #263c30;
      font: inherit;
      font-size: .9rem;
      outline: none;
    }

    input:focus, select:focus {
      border-color: #59866a;
      box-shadow: 0 0 0 3px rgb(65 116 82 / 12%);
    }

    .search-button {
      min-height: 45px;
      padding: .7rem 1.2rem;
      border: 0;
      border-radius: 9px;
      background: #1d4934;
      color: #fff;
      font-weight: 700;
      cursor: pointer;
      transition: background .2s, transform .2s;
      white-space: nowrap;
    }

    .search-button:hover { background: #286044; transform: translateY(-1px); }
    .search-button span { margin-right: .3rem; font-size: 1.1rem; }

    .results-section, .history-section { margin-top: 1.5rem; }

    .results-heading { margin-bottom: .7rem; }

    .result-count {
      display: grid;
      place-items: center;
      min-width: 34px;
      height: 34px;
      padding: 0 .5rem;
      border-radius: 10px;
      background: #edf3ee;
      color: #24563e;
      font-weight: 800;
    }

    .results, .history-list { list-style: none; padding: 0; margin: 0; }

    .medicine-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      padding: 1rem .2rem;
      border-bottom: 1px solid #edf0ed;
    }

    .medicine-row:last-child, .history-list li:last-child { border-bottom: 0; }

    .medicine-info, .history-medicine {
      display: flex;
      align-items: center;
      gap: .8rem;
      min-width: 0;
    }

    .medicine-icon {
      display: grid;
      place-items: center;
      width: 40px;
      height: 40px;
      flex-shrink: 0;
      border-radius: 11px;
      background: #edf3ee;
      color: #2c6546;
      font-size: 1.25rem;
    }

    .medicine-info strong { display: block; color: #294536; font-size: .93rem; }
    .medicine-info small { display: block; margin-top: .25rem; color: #87928b; font-size: .77rem; }

    .actions { display: flex; gap: .55rem; flex-shrink: 0; }

    .actions button {
      padding: .62rem .85rem;
      border-radius: 8px;
      font-size: .8rem;
      font-weight: 700;
      cursor: pointer;
      transition: background .2s;
    }

    .actions button:disabled { opacity: .55; cursor: wait; }

    .available-button {
      border: 1px solid #cfe5d4;
      background: #edf6ef;
      color: #28623e;
    }

    .available-button:hover:not(:disabled) { background: #dcefe0; }

    .unavailable-button {
      border: 1px solid #f0d9d5;
      background: #fff4f2;
      color: #a44b40;
    }

    .unavailable-button:hover:not(:disabled) { background: #fce5e1; }

    .history-list li {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      padding: .9rem .2rem;
      border-bottom: 1px solid #edf0ed;
    }

    .history-medicine strong { color: #344b3d; font-size: .88rem; }

    .history-dot {
      width: 9px;
      height: 9px;
      flex-shrink: 0;
      border-radius: 50%;
      background: #4b9a63;
    }

    .history-dot.out { background: #c46b60; }

    .status-pill {
      padding: .35rem .7rem;
      border-radius: 20px;
      background: #edf6ef;
      color: #28623e;
      font-size: .76rem;
      font-weight: 700;
      white-space: nowrap;
    }

    .status-pill.out { background: #fff0ee; color: #a44b40; }

    .empty-state {
      display: grid;
      justify-items: center;
      text-align: center;
      padding: 2.5rem 1rem;
      margin-top: 1rem;
      background: #fff;
      border: 1px dashed #d9e2da;
      border-radius: 14px;
      color: #78847c;
    }

    .empty-state h2, .empty-state h3 {
      color: #294536;
      margin: .8rem 0 .35rem;
    }

    .empty-state p { margin: 0; font-size: .88rem; line-height: 1.5; }
    .empty-icon { color: #9aab9e; font-size: 2rem; }
    .compact { padding: 1.7rem 1rem; }

    .loader {
      width: 27px;
      height: 27px;
      border: 3px solid #e1eae2;
      border-top-color: #286044;
      border-radius: 50%;
      animation: spin .8s linear infinite;
    }

    @keyframes spin { to { transform: rotate(360deg); } }

    @media (max-width: 720px) {
      .stock { padding: 1.5rem 1rem 3rem; }
      form { grid-template-columns: 1fr 1fr; }
      .medicine-row { align-items: flex-start; flex-direction: column; }
      .actions { width: 100%; }
      .actions button { flex: 1; }
    }

    @media (max-width: 480px) {
      .page-header { align-items: flex-start; }
      .header-icon { width: 44px; height: 44px; font-size: 1.4rem; }
      .search-card, .results-section, .history-section { padding: 1rem; }
      form { grid-template-columns: 1fr; }
      .search-button { width: 100%; }
      .section-heading h2 { font-size: 1.05rem; }
    }
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
