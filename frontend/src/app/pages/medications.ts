import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { GeneriquesComponent } from '../components/generiques';
import { HttpClient, HttpParams } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subject, catchError, debounceTime, distinctUntilChanged, map, of, switchMap } from 'rxjs';

export interface Medication {
  code: string; nom: string; dci1: string | null; dosage1: number | null; uniteDosage1: string | null;
  forme: string | null; presentation: string | null; ppv: number | null; tauxRemboursement: number | null;
  princepsGenerique: string | null; dosageBrut: string | null;
}

/** Nombre minimum de caractères avant de lancer la recherche. */
export const MIN_CHARS = 2;

@Component({
  selector: 'app-medications', imports: [DecimalPipe, GeneriquesComponent],
  template: `
    <main>
      <h1>Rechercher un médicament</h1>
      <p class="hint">Catalogue officiel CNOPS. Tapez au moins {{ minChars }} lettres.</p>
      <input type="search" aria-label="Nom du médicament" placeholder="Ex. : Doliprane, Augmentin…"
             autocomplete="off" [value]="query" (input)="onInput($any($event.target).value)" />
      @if (loading()) { <p class="hint">Recherche…</p> }
      @if (error()) { <p role="alert">{{ error() }}</p> }
      @if (searched() && !loading() && !error() && results().length === 0) {
        <p class="hint">Aucun médicament trouvé pour « {{ query }} ».</p>
      }
      <ul>
        @for (m of results(); track m.code) {
          <li>
            <div class="top">
              <strong>{{ m.nom }}</strong> <span>{{ dosage(m) }}</span>
              @if (m.princepsGenerique === 'G') { <span class="badge gen">Générique</span> }
              @if (m.princepsGenerique === 'P') { <span class="badge">Princeps</span> }
            </div>
            <div class="sub">{{ m.dci1 }} · {{ m.forme }} · {{ m.presentation }}</div>
            <div class="price">
              @if (m.ppv !== null) { <span>{{ m.ppv | number:'1.2-2' }} MAD</span> }
              @if (m.tauxRemboursement !== null) { <span>Remboursé à {{ m.tauxRemboursement }} %</span> }
            </div>
            <!-- US16 : génériques moins chers -->
            <app-generiques [code]="m.code" />
          </li>
        }
      </ul>
    </main>
  `,
  styles: [`main { max-width: 760px; margin: 2rem auto; padding: 1rem 1.5rem; } h1 { color: var(--couleur-bandeau); }
    .hint { color: #555; } [role=alert] { color: #a11525; }
    input { width: 100%; padding: .8rem; font: inherit; border: 1px solid #bbb; border-radius: 6px; box-sizing: border-box; }
    ul { list-style: none; padding: 0; display: grid; gap: .6rem; margin-top: 1rem; }
    li { background: #fff; border: 1px solid var(--couleur-bordure); border-radius: 8px; padding: .8rem 1rem; }
    .top { display: flex; gap: .5rem; align-items: center; flex-wrap: wrap; } .sub { color: #555; font-size: .9rem; margin-top: .2rem; }
    .price { display: flex; gap: 1rem; margin-top: .3rem; color: var(--couleur-or-texte); font-weight: 600; }
    .badge { font-size: .75rem; padding: .1rem .5rem; border-radius: 999px; background: #eee; } .badge.gen { background: #f3ecdb; color: var(--couleur-or-texte); }`],
})
export class MedicationsPage {
  private readonly http = inject(HttpClient);
  private readonly queries = new Subject<string>();
  readonly minChars = MIN_CHARS;
  query = '';
  readonly results = signal<Medication[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly searched = signal(false);

  constructor() {
    this.queries.pipe(
      map(q => q.trim()),
      debounceTime(300),
      distinctUntilChanged(),
      switchMap(q => this.search(q)),
      takeUntilDestroyed(),
    ).subscribe(r => { this.loading.set(false); this.results.set(r); });
  }

  onInput(value: string) { this.query = value; this.queries.next(value); }

  /** Appel au catalogue ; ne lance rien sous MIN_CHARS caractères. */
  search(q: string): Observable<Medication[]> {
    if (q.length < MIN_CHARS) { this.searched.set(false); this.error.set(''); return of([]); }
    this.loading.set(true); this.error.set('');
    return this.http.get<Medication[]>('/api/medications/search', { params: new HttpParams().set('q', q) }).pipe(
      map(r => { this.searched.set(true); return r ?? []; }),
      catchError(() => { this.error.set('La recherche est indisponible pour le moment.'); return of([]); }),
    );
  }

  dosage(m: Medication) { return m.dosageBrut ? `${m.dosageBrut} ${m.uniteDosage1 ?? ''}`.trim() : ''; }
}
