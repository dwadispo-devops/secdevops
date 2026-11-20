import { Component, effect, inject, input, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { PharmacyRating, PharmacyRatingService } from '../services/pharmacy-rating.service';

@Component({
  selector: 'app-pharmacy-rating',
  template: `
    <section aria-label="Noter la pharmacie">
      <h3>Votre avis après le retrait</h3>
      @if (loading()) { <p role="status">Chargement de votre note…</p> }
      @if (error()) { <p role="alert">{{ error() }}</p> }
      @if (success()) { <p role="status">Votre note a été enregistrée. Merci !</p> }
      @if (rating(); as saved) {
        <p>Votre note : <strong>{{ saved.stars }} / 5 étoiles</strong></p>
      } @else if (loaded() && !loading()) {
        <fieldset [disabled]="sending()">
          <legend>Choisissez une note de 1 à 5 étoiles</legend>
          @for (star of stars; track star) {
            <label>
              <input type="radio" name="pharmacy-stars" [value]="star"
                     [checked]="selected() === star" (change)="selected.set(star)"
                     [attr.aria-label]="star + (star === 1 ? ' étoile' : ' étoiles')" />
              <span aria-hidden="true">{{ star <= selected() ? '★' : '☆' }}</span>
            </label>
          }
          <button type="button" (click)="submit()" [disabled]="selected() < 1 || sending()">
            {{ sending() ? 'Enregistrement…' : 'Envoyer ma note' }}
          </button>
        </fieldset>
      }
      @if (!loading() && !loaded() && error()) {
        <button type="button" (click)="load()">Réessayer</button>
      }
    </section>
  `,
  styles: [`
    section { margin-top: 1rem; padding-top: .75rem; border-top: 1px solid #cbd5e1; }
    fieldset { border: 0; padding: .5rem 0; } label { display: inline-flex; align-items: center; padding: .4rem; cursor: pointer; }
    label span { font-size: 1.8rem; color: #855000; } input { accent-color: #166534; }
    button { display: block; margin-top: .75rem; padding: .6rem 1rem; cursor: pointer; }
    button:disabled { opacity: .5; cursor: default; } [role=alert] { color: #a11525; }
  `],
})
export class PharmacyRatingComponent {
  readonly requestId = input.required<string>();
  readonly reservationId = input.required<string>();
  private readonly api = inject(PharmacyRatingService);
  private requests = new Subscription();
  readonly stars = [1, 2, 3, 4, 5];
  readonly selected = signal(0);
  readonly rating = signal<PharmacyRating | null>(null);
  readonly loading = signal(false);
  readonly loaded = signal(false);
  readonly sending = signal(false);
  readonly error = signal('');
  readonly success = signal(false);

  constructor() {
    effect(onCleanup => {
      this.requestId(); this.reservationId();
      this.requests.unsubscribe(); this.requests = new Subscription();
      this.selected.set(0); this.rating.set(null); this.success.set(false); this.sending.set(false);
      this.load();
      onCleanup(() => this.requests.unsubscribe());
    });
  }

  load() {
    this.loaded.set(false); this.loading.set(true); this.error.set('');
    this.requests.add(this.api.get(this.requestId(), this.reservationId()).subscribe({
      next: rating => { this.rating.set(rating); this.loaded.set(true); this.loading.set(false); },
      error: e => { this.error.set(this.message(e.status)); this.loading.set(false); },
    }));
  }

  submit() {
    const stars = this.selected();
    if (!this.loaded() || this.loading() || this.sending() || this.rating() || !Number.isInteger(stars) || stars < 1 || stars > 5) return;
    this.sending.set(true); this.error.set('');
    this.requests.add(this.api.create(this.requestId(), this.reservationId(), stars).subscribe({
      next: rating => { this.rating.set(rating); this.sending.set(false); this.success.set(true); },
      error: e => {
        this.sending.set(false);
        this.error.set(this.message(e.status));
        // Recharger la note après un conflit (par exemple un autre onglet).
        if (e.status === 409) {
          this.loaded.set(false);
          this.loading.set(true);
          this.requests.add(this.api.get(this.requestId(), this.reservationId()).subscribe({
            next: rating => { this.rating.set(rating); this.loaded.set(true); this.loading.set(false); },
            error: err => { this.error.set(this.message(err.status)); this.loading.set(false); },
          }));
        } else if ([401, 403, 404].includes(e.status)) this.loaded.set(false);
      },
    }));
  }

  private message(status: number) {
    return ({ 400: 'Choisissez une note entière entre 1 et 5.',
      401: 'Session invalide ou expirée. Reconnectez-vous.',
      403: 'La notation est réservée aux patients.',
      404: 'Réservation introuvable pour ce compte.',
      409: 'Notation indisponible : retrait non confirmé ou note déjà enregistrée.',
    } as Record<number, string>)[status] ?? 'Impossible de joindre le serveur. Réessayez.';
  }
}
