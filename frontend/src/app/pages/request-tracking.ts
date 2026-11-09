import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, catchError, forkJoin, map, of, switchMap, timer } from 'rxjs';
import { CartePharmaciesComponent, PointCarte } from '../components/carte-pharmacies';
import { ReservationQrComponent } from '../components/reservation-qr';
import { PharmacyService } from '../services/pharmacy.service';

/** Format de GET /api/availability-requests/{id}/responses (ReviewDtos.Request). */
export interface ReponsePharmacie {
  requestId: string; pharmacyId: string; pharmacyName: string; status: string; createdAt: string;
  prescriptionId: string | null; price: number | null; comment: string | null; respondedAt: string | null;
}
/** US12 : format de ReservationDtos.Reservation. status : ACTIVE, RETIREE, ANNULEE ou EXPIREE. */
export interface Reservation {
  id: string; requestId: string; pharmacyId: string; pharmacyName: string; code: string;
  status: string; price: number | null; createdAt: string; expiresAt: string;
}
export interface DemandeSuivie { id: string; latitude: number; longitude: number; radiusM: number; }

/** Clé partagée avec la page de demande (availability-request.ts). */
export const CLE_SUIVI = 'demande_suivie';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const ORDRE: Record<string, number> = { AVAILABLE: 0, PENDING: 1, UNAVAILABLE: 2 };
export const COULEURS: Record<string, string> = { AVAILABLE: '#16a34a', PENDING: '#9ca3af', UNAVAILABLE: '#dc2626' };
export const RAFRAICHISSEMENT_MS = 10_000;

export function lireDemandeSuivie(): DemandeSuivie | null {
  try {
    const v = JSON.parse(sessionStorage.getItem(CLE_SUIVI) ?? 'null');
    return v && typeof v.id === 'string' && UUID.test(v.id) ? v : null;
  } catch { return null; }
}

/** Disponibles d'abord (du moins cher au plus cher), puis en attente, puis indisponibles. */
export function trierReponses(liste: ReponsePharmacie[]): ReponsePharmacie[] {
  return [...liste].sort((a, b) =>
    (ORDRE[a.status] ?? 3) - (ORDRE[b.status] ?? 3)
    || (a.price ?? Number.MAX_VALUE) - (b.price ?? Number.MAX_VALUE)
    || a.pharmacyName.localeCompare(b.pharmacyName));
}

@Component({
  selector: 'app-request-tracking',
  imports: [DecimalPipe, RouterLink, CartePharmaciesComponent, ReservationQrComponent],
  template: `
    <main class="suivi">
      <h1>Suivi de ma demande</h1>
      @if (id) { <p class="ref">Référence : {{ id }}</p> }
      @if (erreur()) {
        <p role="alert">{{ erreur() }}
          @if (nonConnecte()) {
            <a routerLink="/connexion" [queryParams]="{ retour: '/suivi-demande/' + id }">Se connecter</a>
          } @else {
            <a routerLink="/demande-disponibilite">Nouvelle demande</a>
          }
        </p>
      }

      @if (!erreur() && !erreurChargementReservation() && reservation(); as res) {
        <section class="reservation" [attr.data-statut]="statutReservation()" role="status">
          @if (reservationActive()) {
            <h2>✅ Réservation confirmée</h2>
            <p>Présentez ce code au comptoir de <strong>{{ res.pharmacyName }}</strong> :</p>
            <app-reservation-qr [code]="res.code" />
            <p class="code" aria-label="Code de retrait">{{ res.code }}</p>
            <p>Valable jusqu’à <strong>{{ expirationLibelle() }}</strong> (heure du Maroc) · validité de 2 heures
              @if (res.price !== null) { · {{ res.price | number: '1.2-2' }} DH, à régler en pharmacie }</p>
          } @else if (statutReservation() === 'EXPIREE') {
            <h2>⌛ Réservation expirée</h2>
            <p>Le code n’a pas été utilisé dans les deux heures. Vous pouvez réserver à nouveau ci-dessous.</p>
          } @else if (res.status === 'RETIREE') {
            <h2>📦 Médicaments retirés</h2>
            <p>Merci d’avoir utilisé DwaDispo.</p>
          } @else {
            <h2>Réservation annulée</h2>
          }
        </section>
      }
      @if (erreurChargementReservation()) {
        <p role="alert">{{ erreurChargementReservation() }}
          @if (nonConnecte()) {
            <a routerLink="/connexion" [queryParams]="{ retour: '/suivi-demande/' + id }">Se connecter</a>
          }
        </p>
      }
      @if (erreurReservation()) { <p role="alert">{{ erreurReservation() }}</p> }

      @if (reponses(); as liste) {
        <div class="resume" role="status">
          <span class="pastille ok">🟢 {{ nb('AVAILABLE') }} disponible(s)</span>
          <span class="pastille attente">⏳ {{ nb('PENDING') }} en attente</span>
          <span class="pastille ko">🔴 {{ nb('UNAVAILABLE') }} indisponible(s)</span>
        </div>
        <p class="maj">{{ liste.length }} pharmacie(s) contactée(s) · mise à jour automatique toutes les 10 secondes</p>

        <section class="grille">
          <div>
            @if (repondues().length === 0) {
              <p class="vide">Aucune pharmacie n’a encore répondu. Les réponses s’afficheront ici automatiquement.</p>
            }
            <ul class="liste">
              @for (r of repondues(); track r.pharmacyId) {
                <li [attr.data-statut]="r.status">
                  <div class="ligne">
                    <strong>{{ r.pharmacyName }}</strong>
                    <span class="statut">{{ libelle(r.status) }}</span>
                  </div>
                  @if (r.price !== null) { <p class="prix">{{ r.price | number: '1.2-2' }} DH</p> }
                  @if (r.comment) { <p class="commentaire">« {{ r.comment }} »</p> }
                  @if (r.status === 'AVAILABLE') {
                    <button type="button" (click)="reserver(r)" [disabled]="!reservationChargee() || !!erreur() || reservationActive() || reservant()">
                      {{ reservant() ? 'Réservation…' : 'Réserver ici' }}
                    </button>
                  }
                </li>
              }
            </ul>
            @if (enAttente().length > 0) {
              <details>
                <summary>{{ enAttente().length }} pharmacie(s) en attente de réponse</summary>
                <ul class="attente">
                  @for (r of enAttente(); track r.pharmacyId) { <li>{{ r.pharmacyName }}</li> }
                </ul>
              </details>
            }
          </div>
          @if (centre(); as c) {
            <div>
              <app-carte-pharmacies [points]="points()" [centre]="c" />
              <p class="legende"><span class="dot ok"></span> Disponible <span class="dot attente"></span> En attente
                <span class="dot ko"></span> Indisponible <span class="dot moi"></span> Votre position</p>
            </div>
          }
        </section>
      } @else if (!erreur()) {
        <p>Chargement des réponses…</p>
      }
    </main>
  `,
  styles: [`
    .suivi { max-width: 1200px; margin: 2rem auto; padding: 0 1.5rem; } h1 { color: #176b55; }
    .ref, .maj, .legende { color: #555; font-size: .9rem; } [role=alert] { color: #a11525; }
    .reservation { margin: 1rem 0; padding: 1.2rem 1.5rem; border-radius: 12px; background: #e8f5ef; border: 2px solid #16a34a; max-width: 560px; }
    .reservation[data-statut='EXPIREE'], .reservation[data-statut='ANNULEE'] { background: #f6f6f6; border-color: #9ca3af; }
    .reservation h2 { margin: 0 0 .5rem; color: #166534; font-size: 1.2rem; } .reservation p { margin: .3rem 0; }
    .code { font-size: 2.4rem; font-weight: 800; letter-spacing: .35em; color: #176b55; font-family: ui-monospace, monospace; margin: .6rem 0 !important; }
    .resume { display: flex; gap: .6rem; flex-wrap: wrap; margin: 1rem 0 .3rem; }
    .pastille { padding: .35rem .8rem; border-radius: 999px; background: #f1f1f1; font-weight: 600; }
    .pastille.ok { background: #dcfce7; color: #166534; } .pastille.ko { background: #fee2e2; color: #991b1b; }
    .grille { display: grid; grid-template-columns: minmax(280px, 420px) 1fr; gap: 1.5rem; margin-top: 1rem; }
    @media (max-width: 800px) { .grille { grid-template-columns: 1fr; } }
    .liste { list-style: none; padding: 0; margin: 0 0 1rem; display: grid; gap: .7rem; }
    .liste li { background: #fff; border: 1px solid #e3e3e3; border-left: 5px solid #9ca3af; border-radius: 10px; padding: .8rem 1rem; }
    .liste li[data-statut='AVAILABLE'] { border-left-color: #16a34a; } .liste li[data-statut='UNAVAILABLE'] { border-left-color: #dc2626; }
    .ligne { display: flex; justify-content: space-between; gap: .5rem; } .statut { font-size: .85rem; color: #555; }
    .prix { font-size: 1.2rem; font-weight: 700; color: #166534; margin: .3rem 0; } .commentaire { color: #444; margin: .2rem 0; }
    .liste button { margin-top: .4rem; padding: .55rem 1.1rem; border-radius: 6px; border: 0; background: #176b55; color: #fff; cursor: pointer; font: inherit; }
    .liste button:disabled { opacity: .5; cursor: default; }
    .vide { color: #555; background: #f6f7f7; padding: .8rem 1rem; border-radius: 8px; }
    details { background: #fff; border: 1px solid #e3e3e3; border-radius: 10px; padding: .6rem 1rem; } summary { cursor: pointer; }
    .attente { columns: 2; font-size: .9rem; color: #555; }
    .dot { display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin: 0 .2rem 0 .8rem; background: #9ca3af; }
    .dot.ok { background: #16a34a; } .dot.ko { background: #dc2626; } .dot.moi { background: #2878e8; }
  `],
})
export class RequestTrackingPage {
  private readonly http = inject(HttpClient);
  private readonly pharmacies = inject(PharmacyService);
  private readonly route = inject(ActivatedRoute);
  private readonly suivie = lireDemandeSuivie();

  readonly id: string | null = this.route.snapshot.paramMap.get('id') ?? this.suivie?.id ?? null;
  readonly reponses = signal<ReponsePharmacie[] | null>(null);
  readonly erreur = signal('');
  readonly nonConnecte = signal(false);
  readonly reservation = signal<Reservation | null>(null);
  readonly reservant = signal(false);
  readonly erreurReservation = signal('');
  readonly erreurChargementReservation = signal('');
  readonly reservationChargee = signal(false);
  private readonly maintenant = signal(Date.now());
  private reservationRevision = 0;
  private readonly coordonnees = signal<Record<string, { lat: number; lng: number }>>({});

  readonly statutReservation = computed(() => {
    const res = this.reservation();
    if (res?.status === 'ACTIVE' && !(Date.parse(res.expiresAt) > this.maintenant())) return 'EXPIREE';
    return res?.status ?? null;
  });
  readonly reservationActive = computed(() => this.statutReservation() === 'ACTIVE');
  readonly expirationLibelle = computed(() => {
    const date = this.reservation()?.expiresAt;
    return date && Number.isFinite(Date.parse(date))
      ? new Intl.DateTimeFormat('fr-FR', { timeZone: 'Africa/Casablanca', dateStyle: 'short', timeStyle: 'medium' }).format(new Date(date))
      : '';
  });
  readonly centre = computed(() =>
    this.suivie && this.suivie.id === this.id ? { lat: this.suivie.latitude, lng: this.suivie.longitude } : null);
  readonly triees = computed(() => trierReponses(this.reponses() ?? []));
  readonly repondues = computed(() => this.triees().filter(r => r.status !== 'PENDING'));
  readonly enAttente = computed(() => this.triees().filter(r => r.status === 'PENDING'));
  readonly points = computed<PointCarte[]>(() => {
    const c = this.coordonnees();
    return this.triees().filter(r => c[r.pharmacyId]).map(r => ({
      id: r.pharmacyId, lat: c[r.pharmacyId].lat, lng: c[r.pharmacyId].lng,
      couleur: COULEURS[r.status] ?? '#9ca3af', titre: r.pharmacyName,
      detail: this.libelle(r.status) + (r.price !== null ? ` – ${r.price} DH` : ''),
    }));
  });

  constructor() {
    if (!this.id || !UUID.test(this.id)) {
      this.erreur.set('Aucune demande à suivre. Envoyez d’abord une demande de disponibilité.');
      return;
    }
    timer(0, 1000).pipe(takeUntilDestroyed()).subscribe(() => this.maintenant.set(Date.now()));
    timer(0, RAFRAICHISSEMENT_MS)
      .pipe(switchMap(() => forkJoin([this.charger(), this.chargerReservation()])), takeUntilDestroyed())
      .subscribe();

    // Coordonnées des pharmacies : on réutilise l'API /nearby (US04) avec la position et le rayon de la demande.
    const s = this.suivie;
    if (s && s.id === this.id) {
      this.pharmacies.getNearbyPharmacies(s.latitude, s.longitude, s.radiusM)
        .pipe(catchError(() => of([])), takeUntilDestroyed())
        .subscribe(liste => this.coordonnees.set(
          Object.fromEntries(liste.map(p => [p.id, { lat: p.latitude, lng: p.longitude }]))));
    }
  }

  private entetes(): { Authorization: string } | null {
    const token = sessionStorage.getItem('access_token');
    return token ? { Authorization: `Bearer ${token}` } : null;
  }

  charger(): Observable<void> {
    const headers = this.entetes();
    this.nonConnecte.set(!headers);
    if (!headers) {
      this.reponses.set(null);
      this.reservation.set(null);
      this.erreur.set('Connectez-vous avec le compte qui a envoyé la demande.');
      return of(undefined);
    }
    return this.http.get<ReponsePharmacie[]>(`/api/availability-requests/${encodeURIComponent(this.id!)}/responses`, { headers }).pipe(
      map((liste): void => { this.erreur.set(''); this.reponses.set(liste ?? []); }),
      catchError(e => {
        this.reponses.set(null);
        if ([401, 403, 404].includes(e.status)) this.reservation.set(null);
        this.nonConnecte.set(e.status === 401);
        this.erreur.set(e.status === 404 ? 'Demande introuvable pour ce compte.'
          : e.status === 401 ? 'Session expirée. Reconnectez-vous.'
          : e.status === 403 ? 'Accès réservé au patient propriétaire de la demande.'
          : 'Impossible de charger les réponses pour le moment. Nouvelle tentative automatique dans 10 secondes.');
        return of(undefined);
      }),
    );
  }

  /** US12 : réservation en cours pour cette demande (404 = aucune). */
  chargerReservation(): Observable<void> {
    const headers = this.entetes();
    const revision = this.reservationRevision;
    if (!headers) {
      this.reservation.set(null);
      this.reservationChargee.set(false);
      this.erreurChargementReservation.set('Connectez-vous pour consulter votre réservation.');
      return of(undefined);
    }
    return this.http.get<Reservation>(`/api/availability-requests/${encodeURIComponent(this.id!)}/reservation`, { headers }).pipe(
      map((res): void => {
        if (revision !== this.reservationRevision) return;
        this.maintenant.set(Date.now());
        this.reservation.set(res);
        this.reservationChargee.set(true);
        this.erreurChargementReservation.set('');
      }),
      catchError(e => {
        if (revision !== this.reservationRevision) return of(undefined);
        this.reservationChargee.set(e.status === 404);
        if ([401, 403, 404].includes(e.status)) this.reservation.set(null);
        this.erreurChargementReservation.set(e.status === 404 ? ''
          : e.status === 401 ? 'Session expirée. Reconnectez-vous pour consulter votre réservation.'
          : e.status === 403 ? 'Accès à la réservation réservé au patient autorisé.'
          : 'Impossible de vérifier votre réservation. Nouvelle tentative automatique dans 10 secondes.');
        if (e.status === 401) this.nonConnecte.set(true);
        return of(undefined);
      }),
    );
  }

  /** US12 / EF21 : réserve chez une pharmacie qui a répondu « disponible » et obtient un code de retrait. */
  reserver(r: ReponsePharmacie): void {
    const headers = this.entetes();
    if (!headers || !this.reservationChargee() || this.erreur() || this.reservant() || this.reservationActive()) return;
    this.reservant.set(true);
    this.erreurReservation.set('');
    this.http.post<Reservation>(`/api/availability-requests/${encodeURIComponent(this.id!)}/reservations`,
      { pharmacyId: r.pharmacyId }, { headers }).subscribe({
      next: res => {
        // Une consultation commencée avant la création ne doit pas effacer son résultat.
        this.reservationRevision++;
        this.maintenant.set(Date.now());
        this.reservant.set(false);
        this.reservation.set(res);
        this.reservationChargee.set(true);
        this.erreurChargementReservation.set('');
      },
      error: e => {
        this.reservant.set(false);
        this.erreurReservation.set(e.error?.message ?? 'Réservation impossible pour le moment. Réessayez.');
      },
    });
  }

  nb(statut: string): number {
    return (this.reponses() ?? []).filter(r => r.status === statut).length;
  }

  libelle(statut: string): string {
    return statut === 'AVAILABLE' ? 'Disponible' : statut === 'UNAVAILABLE' ? 'Indisponible' : 'En attente';
  }
}
