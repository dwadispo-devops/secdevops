import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Etat, LigneOrdonnance, OrdonnanceService, Produit, etatDe } from '../services/ordonnance';
import { CatalogueService } from '../services/catalogue.service';
import { FormsModule } from '@angular/forms';
import { PrescriptionEstimateComponent } from '../components/prescription-estimate';
import { GeneriquesComponent } from '../components/generiques';

@Component({
  selector: 'app-verification',
  imports: [RouterLink, FormsModule, PrescriptionEstimateComponent, GeneriquesComponent],
  styles: [
    `
      .intro { max-width: 65ch; }
      .lignes { list-style: none; margin: 1.5rem 0; padding: 0; display: grid; gap: 1rem; }
      .ligne {
        padding: 1rem 1.25rem;
        border: 1px solid var(--couleur-bordure);
        border-left-width: 6px;
        border-radius: var(--rayon);
        background: var(--couleur-carte);
      }
      .ligne[data-etat='confirmee'] { border-left-color: #16a34a; }
      .ligne[data-etat='a-verifier'] { border-left-color: #ea580c; }
      .ligne[data-etat='non-lue'] { border-left-color: #dc2626; }
      .ligne[data-etat='supprimee'] { border-left-color: #94a3b8; opacity: 0.7; }
      .ligne[data-etat='supprimee'] .lu { text-decoration: line-through; }
      .entete { display: flex; justify-content: space-between; align-items: flex-start; gap: 0.75rem; flex-wrap: wrap; }
      .lu, .retenu { margin: 0 0 0.5rem; }
      .confiance { margin: 0 0 0.5rem; font-size: 0.85rem; color: #475569; }
      .badge { padding: 0.15rem 0.6rem; border-radius: 999px; color: #fff; font-size: 0.8rem; font-weight: 600; }
      .badge[data-etat='confirmee'] { background: #15803d; }
      .badge[data-etat='a-verifier'] { background: #c2410c; }
      .badge[data-etat='non-lue'] { background: #b91c1c; }
      .badge[data-etat='supprimee'] { background: #64748b; }
      .correction { margin: 0.75rem 0; padding: 0.75rem; border-radius: 8px; background: var(--couleur-fond); }
      .titre, .correction label { display: block; margin: 0.5rem 0 0.25rem; font-weight: 600; }
      .correction input {
        width: 100%;
        max-width: 28rem;
        padding: 0.55rem 0.75rem;
        border: 1px solid var(--couleur-bordure);
        border-radius: 8px;
        font: inherit;
      }
      .choix { list-style: none; margin: 0.25rem 0 0; padding: 0; display: grid; gap: 0.4rem; }
      .choix button {
        width: 100%;
        padding: 0.6rem 0.75rem;
        border: 1px solid var(--couleur-bordure);
        border-radius: 8px;
        background: #fff;
        font: inherit;
        text-align: left;
        cursor: pointer;
      }
      .choix button:hover { border-color: var(--couleur-principale); }
      .actions { display: flex; gap: 0.5rem; flex-wrap: wrap; margin-top: 0.5rem; }
      .mini {
        padding: 0.4rem 0.75rem;
        border: 1px solid var(--couleur-bordure);
        border-radius: 8px;
        background: #fff;
        font: inherit;
        cursor: pointer;
      }
      .mini.danger { border-color: #fecaca; color: #b91c1c; }
      .vide, .aide { margin: 0.5rem 0 0; color: #475569; }
      .pied { display: flex; justify-content: space-between; gap: 0.75rem; flex-wrap: wrap; margin-top: 1rem; }
      button.btn { border: 0; font: inherit; cursor: pointer; }
      button.btn:disabled { opacity: 0.45; cursor: not-allowed; }
      .btn.secondaire { border: 1px solid var(--couleur-principale); background: #fff; color: var(--couleur-principale); }
      @media (max-width: 480px) {
        .pied .btn { width: 100%; text-align: center; }
      }
    `,
  ],
  template: `
    <section class="page">
      <h1>Vérifiez votre ordonnance</h1>
      <p class="intro">
        Voici les médicaments lus sur votre ordonnance. La lecture automatique peut se tromper :
        vérifiez chaque ligne, corrigez-la si besoin, puis continuez.
      </p>

      @if (service.lignes().length === 0) {
        <p class="vide" role="status">
          Aucune ordonnance à vérifier. <a routerLink="/scan">Scanner une ordonnance</a>
          ou <a routerLink="/demande-disponibilite">saisir vos médicaments à la main</a>.
        </p>
      }

      <ul class="lignes">
        @for (ligne of service.lignes(); track ligne.id) {
          @let etat = etatDe(ligne);
          <li class="ligne" [attr.data-etat]="etat">
            <div class="entete">
              <p class="lu">Lu : <strong>« {{ ligne.texteLu }} »</strong></p>
              <span class="badge" [attr.data-etat]="etat">{{ libelle(etat) }}</span>
            </div>
            <p class="confiance">Confiance de la lecture : {{ confiance(ligne) }} %</p>

            @if (ligne.supprimee) {
              <button type="button" class="mini" (click)="service.retablir(ligne.id)">
                Rétablir cette ligne
              </button>
            } @else {
              <p class="retenu">
                @if (ligne.choix) {
                  Médicament retenu : <strong>{{ ligne.choix.nom }}</strong>
                } @else {
                  Aucun médicament confirmé pour cette ligne.
                }
              </p>
              <!-- US16 : génériques moins chers du médicament retenu -->
              @if (ligne.choix && enCorrection() !== ligne.id) { <app-generiques [code]="ligne.choix.id" /> }

              <label [attr.for]="'quantite-' + ligne.id">Quantité de conditionnements (boîtes, flacons…)</label>
              <input type="number" [id]="'quantite-' + ligne.id" min="1" max="10000" step="1"
                     [ngModel]="ligne.quantite ?? null" (ngModelChange)="service.quantifier(ligne.id, $event)" />
              <label>
                <input type="checkbox" [ngModel]="ligne.quantiteConfirmee ?? false"
                       (ngModelChange)="service.confirmerQuantite(ligne.id, $event)" />
                Je confirme la quantité et sa correspondance avec le conditionnement du médicament retenu.
              </label>

              @if (!ligne.choix || enCorrection() === ligne.id) {
                <div class="correction">
                  @if (ligne.suggestions.length > 0) {
                    <p class="titre">Suggestions du catalogue</p>
                    <ul class="choix">
                      @for (s of ligne.suggestions; track s.id) {
                        <li><button type="button" (click)="choisir(ligne, s)">{{ s.nom }}</button></li>
                      }
                    </ul>
                  }

                  <label [attr.for]="'recherche-' + ligne.id">
                    Autre médicament : rechercher dans le catalogue
                  </label>
                  <input
                    type="search"
                    autocomplete="off"
                    placeholder="Tapez au moins 2 lettres"
                    [id]="'recherche-' + ligne.id"
                    [value]="terme(ligne.id)"
                    (input)="setTerme(ligne.id, $event)"
                  />

                  @if (resultats(ligne.id).length > 0) {
                    <ul class="choix">
                      @for (r of resultats(ligne.id); track r.id) {
                        <li><button type="button" (click)="choisir(ligne, r)">{{ r.nom }}</button></li>
                      }
                    </ul>
                  } @else if (terme(ligne.id).trim().length >= 2) {
                    <p class="vide">Aucun médicament du catalogue ne correspond.</p>
                  }

                  @if (etat === 'non-lue') {
                    <p><a routerLink="/scan">Reprendre la photo</a></p>
                  }
                </div>
              }

              <div class="actions">
                @if (ligne.choix && enCorrection() !== ligne.id) {
                  <button type="button" class="mini" (click)="corriger(ligne.id)">Corriger</button>
                } @else if (enCorrection() === ligne.id) {
                  <button type="button" class="mini" (click)="fermer()">Annuler</button>
                }
                <button type="button" class="mini danger" (click)="service.supprimer(ligne.id)">
                  Supprimer la ligne
                </button>
              </div>
            }
          </li>
        }
      </ul>

      @if (service.lignes().length > 0) { <app-prescription-estimate [lines]="service.lignes()" /> }

      <div class="pied">
        <a class="btn secondaire" routerLink="/scan">Reprendre la photo</a>
        <button
          type="button"
          class="btn"
          [disabled]="!service.peutContinuer()"
          (click)="continuer()"
        >
          Continuer
        </button>
      </div>

      @if (!service.peutContinuer()) {
        <p class="aide" role="status">
          Confirmez ou supprimez chaque ligne pour pouvoir continuer.
        </p>
      }
    </section>
  `,
})
export class VerificationPage {
  protected readonly service = inject(OrdonnanceService);
  private readonly router = inject(Router);
  private readonly catalogue = inject(CatalogueService);

  protected readonly etatDe = etatDe;
  protected readonly enCorrection = signal<number | null>(null);
  private readonly termes = signal<Record<number, string>>({});
  private readonly trouves = signal<Record<number, Produit[]>>({});

  private readonly libelles: Record<Etat, string> = {
    confirmee: 'Confirmé',
    'a-verifier': 'À vérifier',
    'non-lue': 'Non lu',
    supprimee: 'Supprimé',
  };

  protected libelle(etat: Etat): string {
    return this.libelles[etat];
  }

  protected confiance(ligne: LigneOrdonnance): number {
    return Math.round(ligne.score * 100);
  }

  protected terme(id: number): string {
    return this.termes()[id] ?? '';
  }

  protected setTerme(id: number, event: Event): void {
    const valeur = (event.target as HTMLInputElement).value;
    this.termes.update((t) => ({ ...t, [id]: valeur }));
    this.catalogue.rechercher(valeur).subscribe((produits) => {
      // ignore une réponse arrivée après une saisie plus récente
      if (this.terme(id) === valeur) this.trouves.update((r) => ({ ...r, [id]: produits }));
    });
  }

  protected resultats(id: number): Produit[] {
    return this.trouves()[id] ?? [];
  }

  protected corriger(id: number): void {
    this.enCorrection.set(id);
  }

  protected fermer(): void {
    this.enCorrection.set(null);
  }

  protected choisir(ligne: LigneOrdonnance, produit: Produit): void {
    this.service.choisir(ligne.id, produit);
    this.termes.update((t) => ({ ...t, [ligne.id]: '' }));
    this.trouves.update((r) => ({ ...r, [ligne.id]: [] }));
    this.enCorrection.set(null);
  }

  protected continuer(): void {
    if (this.service.peutContinuer()) {
      this.router.navigateByUrl('/demande-disponibilite');
    }
  }
}
