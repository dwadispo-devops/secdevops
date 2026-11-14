import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { forkJoin } from 'rxjs';

import { MyDuty, MyDutyService, MyPharmacy } from '../services/my-duty.service';

/** US15 (amélioration) : le pharmacien déclare les périodes de garde de ses pharmacies. */
@Component({
  selector: 'app-pharmacist-duties',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <main class="duties">
      <h1>Mes gardes</h1>
      <p>
        Déclarez les jours où votre pharmacie est de garde : elle apparaîtra aux patients dans la page
        <a routerLink="/garde">Pharmacies de garde</a>.
      </p>
      <p><a routerLink="/pharmacien/demandes">← Demandes reçues</a></p>

      @if (error()) { <p role="alert" class="error">{{ error() }}</p> }
      @if (success()) { <p role="status" class="success">{{ success() }}</p> }
      @if (loading()) { <p role="status">Chargement…</p> }

      @if (!loading() && pharmacies().length === 0 && !error()) {
        <p>Aucune pharmacie n’est rattachée à votre compte.</p>
      }

      @if (pharmacies().length > 0) {
        <form class="duty-form" (ngSubmit)="submit()">
          <h2>Déclarer une garde</h2>
          <label>
            Pharmacie
            <select name="pharmacyId" [(ngModel)]="pharmacyId" required>
              @for (p of pharmacies(); track p.id) { <option [value]="p.id">{{ p.name }}</option> }
            </select>
          </label>
          <label>
            Du
            <input type="date" name="startsOn" [(ngModel)]="startsOn" required />
          </label>
          <label>
            Au (inclus)
            <input type="date" name="endsOn" [(ngModel)]="endsOn" [min]="startsOn" required />
          </label>
          <button type="submit" [disabled]="sending()">
            {{ sending() ? 'Enregistrement…' : 'Ajouter la garde' }}
          </button>
        </form>
      }

      <section aria-label="Mes périodes de garde">
        <h2>Périodes déclarées</h2>
        @for (d of duties(); track d.id) {
          <article class="duty">
            <div>
              <strong>{{ d.pharmacyName }}</strong>
              <span>du {{ format(d.startsOn) }} au {{ format(d.endsOn) }}</span>
              <span class="badge">{{ statut(d) }}</span>
            </div>
            <button type="button" (click)="remove(d)" [disabled]="sending()">Supprimer</button>
          </article>
        } @empty {
          @if (!loading()) { <p>Aucune garde déclarée.</p> }
        }
      </section>
    </main>
  `,
  styles: [`
    .duties { max-width: 760px; margin: 2rem auto; padding: 0 1rem; display: grid; gap: 1rem; }
    .duty-form { display: grid; gap: .75rem; padding: 1rem; border: 1px solid #dfe7e3; border-radius: 12px; }
    .duty-form label { display: grid; gap: .25rem; }
    .duty { display: flex; justify-content: space-between; align-items: center; gap: 1rem;
            padding: .75rem 1rem; border: 1px solid #dfe7e3; border-radius: 12px; margin-bottom: .5rem; }
    .duty div { display: grid; gap: .2rem; }
    .badge { font-size: .85rem; color: #0f6e56; }
    .error { color: #a32d2d; }
    .success { color: #0f6e56; }
  `],
})
export class PharmacistDutiesPage implements OnInit {
  private readonly service = inject(MyDutyService);

  readonly pharmacies = signal<MyPharmacy[]>([]);
  readonly duties = signal<MyDuty[]>([]);
  readonly loading = signal(false);
  readonly sending = signal(false);
  readonly error = signal('');
  readonly success = signal('');

  pharmacyId = '';
  startsOn = PharmacistDutiesPage.today();
  endsOn = PharmacistDutiesPage.today();

  /** Le Maroc est à l'heure GMT (UTC+0) : la date UTC est la date locale. */
  static today(): string {
    return new Date().toISOString().slice(0, 10);
  }

  ngOnInit(): void {
    this.loading.set(true);
    forkJoin({ pharmacies: this.service.myPharmacies(), duties: this.service.list() }).subscribe({
      next: ({ pharmacies, duties }) => {
        this.pharmacies.set(pharmacies);
        this.duties.set(duties);
        this.pharmacyId = pharmacies[0]?.id ?? '';
        this.loading.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(this.message(e));
      },
    });
  }

  submit(): void {
    this.error.set('');
    this.success.set('');
    if (!this.pharmacyId || !this.startsOn || !this.endsOn) {
      this.error.set('Choisissez une pharmacie et les deux dates.');
      return;
    }
    if (this.endsOn < this.startsOn) {
      this.error.set('La date de fin doit être égale ou postérieure à la date de début.');
      return;
    }
    this.sending.set(true);
    this.service.create({ pharmacyId: this.pharmacyId, startsOn: this.startsOn, endsOn: this.endsOn }).subscribe({
      next: (duty) => {
        this.duties.update((list) => [duty, ...list]);
        this.success.set('Garde enregistrée. Elle est visible par les patients pendant cette période.');
        this.sending.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.error.set(this.message(e));
        this.sending.set(false);
      },
    });
  }

  remove(duty: MyDuty): void {
    this.error.set('');
    this.success.set('');
    this.sending.set(true);
    this.service.delete(duty.id).subscribe({
      next: () => {
        this.duties.update((list) => list.filter((d) => d.id !== duty.id));
        this.success.set('Garde supprimée.');
        this.sending.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.error.set(this.message(e));
        this.sending.set(false);
      },
    });
  }

  statut(d: MyDuty): string {
    const today = PharmacistDutiesPage.today();
    if (d.endsOn < today) return 'Terminée';
    if (d.startsOn > today) return 'À venir';
    return 'En cours';
  }

  format(iso: string): string {
    const [y, m, d] = iso.split('-');
    return `${d}/${m}/${y}`;
  }

  private message(e: HttpErrorResponse): string {
    const serveur = e.error?.detail || e.error?.message;
    if (serveur) return serveur;
    switch (e.status) {
      case 401:
      case 403: return 'Connectez-vous avec un compte pharmacien.';
      case 404: return 'Pharmacie ou garde introuvable.';
      case 409: return 'Cette pharmacie a déjà une garde sur cette période.';
      case 400: return 'Dates invalides (période terminée, fin avant début, ou plus de 31 jours).';
      default: return 'Le serveur ne répond pas. Réessayez dans un instant.';
    }
  }
}
