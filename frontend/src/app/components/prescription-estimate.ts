import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { LigneOrdonnance } from '../services/ordonnance';
import { PrescriptionEstimate, PrescriptionEstimateService } from '../services/prescription-estimate.service';

@Component({
  selector: 'app-prescription-estimate',
  imports: [DecimalPipe, RouterLink],
  template: `
    <section aria-label="Estimation indicative de l’ordonnance">
      <h2>Coût estimé de mon ordonnance</h2>
      <p>Simulation selon le catalogue CNOPS 2014. Les prix peuvent avoir changé ; le prix en pharmacie peut différer.</p>
      <p>La quantité désigne le nombre de conditionnements du produit sélectionné, pas le nombre de comprimés.</p>
      @if (!ready()) {
        <p role="status">Confirmez chaque médicament et sa quantité entière (de 1 à 10000 conditionnements) pour estimer le coût.</p>
      }
      <button type="button" (click)="calculate()" [disabled]="!ready() || loading()">
        {{ loading() ? 'Calcul en cours…' : 'Estimer le coût' }}
      </button>
      @if (error()) { <p role="alert">{{ error() }}
        @if (loginRequired()) { <a routerLink="/connexion" [queryParams]="{ retour: '/verification' }">Se connecter</a> }
      </p> }
      @if (estimate(); as result) {
        <div class="tableau">
          <table>
            <caption>Détail des prix publics du catalogue (MAD)</caption>
            <thead><tr><th>Produit et conditionnement</th><th>Quantité</th><th>PPV</th><th>Coût estimé</th><th>Taux catalogue</th></tr></thead>
            <tbody>
              @for (line of result.lines; track $index) {
                <tr>
                  <td>{{ line.name ?? line.code }}<br>{{ line.presentation ?? 'Conditionnement inconnu' }}
                    @if (line.reason) { <p>{{ line.reason }}</p> }</td>
                  <td>{{ line.quantity }}</td>
                  <td>{{ line.unitPrice === null ? 'Indisponible' : (line.unitPrice | number:'1.2-2') }}</td>
                  <td>{{ line.estimatedCost === null ? 'Non calculable' : (line.estimatedCost | number:'1.2-2') }}</td>
                  <td>{{ line.catalogRate === null ? 'Non renseigné' : line.catalogRate + ' %' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        @if (result.complete) {
          <p class="total">Coût total estimé : {{ result.totalEstimatedCost | number:'1.2-2' }} MAD</p>
        } @else {
          <p>Estimation incomplète. Sous-total des lignes calculables : {{ result.knownSubtotal | number:'1.2-2' }} MAD.</p>
          <p>Coût total estimé : non calculable pour l’ensemble de l’ordonnance.</p>
        }
        <p><strong>Remboursement non calculable</strong></p>
        <p>{{ result.reimbursementExplanation }}</p>
        <p>Reste à charge : non calculable. Le taux du catalogue n’est pas une garantie de remboursement personnel.</p>
        <small>{{ result.source }}. Aucun résultat n’est enregistré.</small>
      }
    </section>
  `,
  styles: [`
    section { margin: 1.5rem 0; padding: 1rem; border: 1px solid #cbd5e1; border-radius: 8px; }
    .tableau { overflow-x: auto; margin-top: 1rem; } table { width: 100%; border-collapse: collapse; }
    th, td { padding: .6rem; text-align: left; border-bottom: 1px solid #cbd5e1; }
    caption { text-align: left; font-weight: 600; padding: .5rem 0; }
    button { padding: .6rem 1rem; cursor: pointer; } button:disabled { opacity: .5; cursor: default; }
    .total { font-weight: 700; } [role=alert] { color: #a11525; }
  `],
})
export class PrescriptionEstimateComponent {
  readonly lines = input.required<readonly LigneOrdonnance[]>();
  private readonly api = inject(PrescriptionEstimateService);
  readonly estimate = signal<PrescriptionEstimate | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly loginRequired = signal(false);
  private request?: Subscription;
  readonly active = computed(() => this.lines().filter(l => !l.supprimee));
  readonly ready = computed(() => this.active().length > 0 && this.active().length <= 100 && this.active().every(l =>
    l.choix && /^[0-9]{13}$/.test(l.choix.id) && Number.isInteger(l.quantite) && l.quantite! >= 1
    && l.quantite! <= 10000 && l.quantiteConfirmee === true));

  constructor() {
    effect(onCleanup => {
      this.lines();
      this.estimate.set(null);
      this.error.set('');
      this.loading.set(false);
      this.loginRequired.set(false);
      this.request?.unsubscribe();
      onCleanup(() => this.request?.unsubscribe());
    });
  }

  calculate(): void {
    if (!this.ready() || this.loading()) return;
    this.estimate.set(null);
    this.error.set('');
    this.loginRequired.set(!sessionStorage.getItem('access_token'));
    if (this.loginRequired()) { this.error.set('Connectez-vous avec un compte patient pour estimer le coût.'); return; }
    this.loading.set(true);
    this.request = this.api.estimate(this.active().map(l => ({
      code: l.choix!.id, quantity: l.quantite!, unitConfirmed: l.quantiteConfirmee === true,
    }))).subscribe({
      next: result => { this.estimate.set(result); this.loading.set(false); },
      error: e => {
        this.loading.set(false);
        this.loginRequired.set(e.status === 401);
        this.error.set(e.status === 401 ? 'Session expirée. Reconnectez-vous.'
          : e.status === 403 ? 'Cette estimation est réservée aux patients.'
          : e.status === 404 ? 'Ordonnance introuvable pour ce compte.'
          : e.status === 400 ? 'Vérifiez les médicaments et les quantités.'
          : 'Estimation indisponible. Réessayez ; aucun montant de remplacement n’est utilisé.');
      },
    });
  }
}
