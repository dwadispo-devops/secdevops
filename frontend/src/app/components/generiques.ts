import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { Subscription } from 'rxjs';
import { GeneriquesService, ReponseGeneriques, estCodeCatalogue } from '../services/generiques.service';

/**
 * US16 – En tant que patient, je veux voir les génériques moins chers ayant la même DCI.
 * Bloc réutilisable : <app-generiques [code]="codeCnops" />. Rien n'est affiché pour un code hors catalogue.
 */
@Component({
  selector: 'app-generiques',
  imports: [DecimalPipe],
  template: `
    @if (codeValide()) {
      <div class="generiques">
        <button type="button" class="ouvrir" (click)="basculer()" [attr.aria-expanded]="ouvert()">
          {{ ouvert() ? 'Masquer les génériques' : 'Voir les génériques moins chers' }}
        </button>

        @if (ouvert()) {
          @if (chargement()) { <p role="status">Recherche des génériques…</p> }
          @if (erreur()) { <p role="alert">{{ erreur() }}</p> }

          @if (resultat(); as r) {
            @if (r.generiques.length === 0) {
              <p class="vide">{{ r.message }}</p>
            } @else {
              <p class="intro">
                Même substance active ({{ r.reference.dci }}), même dosage et même forme.
                @if (r.reference.prixUnitaire !== null) {
                  Votre médicament : {{ r.reference.prixUnitaire | number:'1.2-2' }} MAD par unité.
                }
              </p>
              <ul>
                @for (g of r.generiques; track g.code) {
                  <li>
                    <div class="haut">
                      <strong>{{ g.nom }}</strong>
                      <span class="economie">−{{ g.economiePourcent }} %</span>
                      @if (g.moinsRembourse) { <span class="alerte">Moins bien remboursé</span> }
                    </div>
                    <div class="detail">{{ g.presentation }}</div>
                    <div class="prix">
                      <span>{{ g.ppv | number:'1.2-2' }} MAD la boîte</span>
                      @if (g.prixUnitaire !== null) { <span>{{ g.prixUnitaire | number:'1.2-2' }} MAD par unité</span> }
                      <span>Remboursement catalogue : {{ g.tauxRemboursement === null ? 'non renseigné' : g.tauxRemboursement + ' %' }}</span>
                    </div>
                  </li>
                }
              </ul>
            }
            <small>{{ r.source }}. Demandez conseil à votre pharmacien ou à votre médecin avant de changer de médicament.</small>
          }
        }
      </div>
    }
  `,
  styles: [`
    .generiques { margin: .5rem 0; }
    .ouvrir { padding: .4rem .8rem; cursor: pointer; border: 1px solid #176b55; color: #176b55;
      background: #fff; border-radius: 6px; font: inherit; }
    ul { list-style: none; padding: 0; margin: .5rem 0; display: grid; gap: .5rem; }
    li { border: 1px solid #e3e3e3; border-radius: 8px; padding: .6rem .8rem; background: #fff; }
    .haut { display: flex; gap: .5rem; align-items: center; flex-wrap: wrap; }
    .economie { font-size: .8rem; padding: .1rem .5rem; border-radius: 999px; background: #dff3ec; color: #176b55; font-weight: 600; }
    .alerte { font-size: .8rem; padding: .1rem .5rem; border-radius: 999px; background: #fdecec; color: #a11525; }
    .detail, small, .intro, .vide { color: #555; font-size: .9rem; }
    .prix { display: flex; gap: 1rem; flex-wrap: wrap; margin-top: .2rem; color: #176b55; font-weight: 600; font-size: .9rem; }
    [role=alert] { color: #a11525; }
  `],
})
export class GeneriquesComponent {
  readonly code = input.required<string>();
  private readonly api = inject(GeneriquesService);

  readonly codeValide = computed(() => estCodeCatalogue(this.code()));
  readonly ouvert = signal(false);
  readonly chargement = signal(false);
  readonly erreur = signal('');
  readonly resultat = signal<ReponseGeneriques | null>(null);
  private requete?: Subscription;

  constructor() {
    // Si le médicament change (correction de la ligne), on repart de zéro
    effect(onCleanup => {
      this.code();
      this.ouvert.set(false);
      this.resultat.set(null);
      this.erreur.set('');
      this.chargement.set(false);
      this.requete?.unsubscribe();
      onCleanup(() => this.requete?.unsubscribe());
    });
  }

  basculer(): void {
    this.ouvert.update(o => !o);
    if (this.ouvert() && !this.resultat() && !this.chargement()) {
      this.charger();
    }
  }

  private charger(): void {
    this.chargement.set(true);
    this.erreur.set('');
    this.requete = this.api.generiques(this.code()).subscribe({
      next: r => { this.resultat.set(r); this.chargement.set(false); },
      error: e => {
        this.chargement.set(false);
        this.erreur.set(e.status === 404 ? 'Ce médicament n’est pas dans le catalogue.'
          : 'Les génériques sont indisponibles pour le moment. Réessayez plus tard.');
      },
    });
  }
}
