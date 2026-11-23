import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { forkJoin } from 'rxjs';

import { MyDuty, MyDutyService, MyPharmacy } from '../services/my-duty.service';

@Component({
  selector: 'app-pharmacist-duties',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <main class="duties-page">
      <header class="page-header">
        <div class="header-content">
          <span class="eyebrow">ESPACE PHARMACIEN</span>
          <h1>Gestion de mes gardes</h1>
          <p>Planifiez les périodes pendant lesquelles votre pharmacie accueille les patients en dehors des horaires habituels.</p>
          <a routerLink="/pharmacien/demandes" class="back-link">← Retour aux demandes reçues</a>
        </div>
        <div class="header-icon" aria-hidden="true">◷</div>
      </header>

      @if (error()) {
        <div class="alert alert-error" role="alert">
          <span>!</span>{{ error() }}
        </div>
      }
      @if (success()) {
        <div class="alert alert-success" role="status">
          <span>✓</span>{{ success() }}
        </div>
      }

      @if (loading()) {
        <div class="loading-state">Chargement de vos informations…</div>
      }

      @if (!loading() && pharmacies().length === 0 && !error()) {
        <section class="empty-state">
          <div class="empty-icon">⌂</div>
          <h2>Aucune pharmacie associée</h2>
          <p>Aucune pharmacie n’est actuellement rattachée à votre compte.</p>
        </section>
      }

      @if (pharmacies().length > 0) {
        <section class="form-card">
          <div class="section-heading">
            <div>
              <span class="section-kicker">NOUVELLE DISPONIBILITÉ</span>
              <h2>Déclarer une période de garde</h2>
              <p>Renseignez votre pharmacie et les dates de disponibilité.</p>
            </div>
            <div class="heading-icon">＋</div>
          </div>

          <form class="duty-form" (ngSubmit)="submit()">
            <div class="form-field pharmacy-field">
              <label for="pharmacyId">Pharmacie concernée</label>
              <select id="pharmacyId" name="pharmacyId" [(ngModel)]="pharmacyId" required>
                @for (p of pharmacies(); track p.id) {
                  <option [value]="p.id">{{ p.name }}</option>
                }
              </select>
            </div>

            <div class="date-fields">
              <div class="form-field">
                <label for="startsOn">Date de début</label>
                <input id="startsOn" type="date" name="startsOn" [(ngModel)]="startsOn" required />
              </div>
              <div class="form-field">
                <label for="endsOn">Date de fin <span>(incluse)</span></label>
                <input id="endsOn" type="date" name="endsOn" [(ngModel)]="endsOn" [min]="startsOn" required />
              </div>
            </div>

            <div class="form-footer">
              <p>Les patients pourront consulter cette disponibilité pendant la période déclarée.</p>
              <button type="submit" [disabled]="sending()">
                {{ sending() ? 'Enregistrement…' : '＋ Ajouter la garde' }}
              </button>
            </div>
          </form>
        </section>
      }

      <section class="periods-section">
        <div class="section-heading periods-heading">
          <div>
            <span class="section-kicker">VOTRE PLANNING</span>
            <h2>Périodes déclarées</h2>
            <p>Consultez et gérez les gardes de vos pharmacies.</p>
          </div>
          <span class="count-badge">{{ duties().length }} période(s)</span>
        </div>

        <div class="duty-list">
          @for (d of duties(); track d.id) {
            <article class="duty-card">
              <div class="duty-symbol">⌂</div>
              <div class="duty-info">
                <strong>{{ d.pharmacyName }}</strong>
                <span class="duty-dates">
                  Du {{ format(d.startsOn) }} au {{ format(d.endsOn) }}
                </span>
              </div>
              <span class="status" [class.status-active]="statut(d) === 'En cours'"
                    [class.status-upcoming]="statut(d) === 'À venir'"
                    [class.status-finished]="statut(d) === 'Terminée'">
                <span class="status-dot"></span>{{ statut(d) }}
              </span>
              <button class="delete-button" type="button" (click)="remove(d)" [disabled]="sending()">
                Supprimer
              </button>
            </article>
          } @empty {
            @if (!loading()) {
              <div class="empty-periods">
                <div class="empty-icon">▤</div>
                <h3>Aucune période déclarée</h3>
                <p>Les gardes que vous ajouterez apparaîtront ici.</p>
              </div>
            }
          }
        </div>
      </section>

      <footer class="page-note">
        <span>ⓘ</span>
        <p>Une garde déclarée permet aux patients de repérer votre pharmacie pendant la période indiquée. Pensez à maintenir votre planning à jour.</p>
      </footer>
    </main>
  `,
  styles: [`
    :host {
      display: block;
      min-height: 100%;
      background: #f5f7f4;
      color: #20382d;
    }

    .duties-page {
      width: min(1100px, calc(100% - 40px));
      margin: 0 auto;
      padding: 34px 0 56px;
      display: grid;
      gap: 26px;
    }

    .page-header {
      position: relative;
      overflow: hidden;
      display: flex;
      justify-content: space-between;
      align-items: center;
      min-height: 205px;
      padding: 34px 42px;
      border-radius: 20px;
      background: linear-gradient(120deg, #123c2d, #20533d);
      color: #fff;
    }

    .page-header::after {
      content: "";
      position: absolute;
      width: 300px;
      height: 300px;
      right: 70px;
      top: -190px;
      border: 1px solid rgba(218, 184, 112, .22);
      border-radius: 50%;
      box-shadow: 0 0 0 35px rgba(218,184,112,.04), 0 0 0 70px rgba(218,184,112,.03);
    }

    .header-content { position: relative; z-index: 1; max-width: 700px; }
    .eyebrow, .section-kicker {
      color: #d8bb7b;
      font-size: .72rem;
      font-weight: 800;
      letter-spacing: .13em;
    }

    .page-header h1 { margin: 10px 0; font-size: clamp(1.8rem, 4vw, 2.5rem); }
    .page-header p { max-width: 620px; margin: 0 0 18px; color: #d9e6de; line-height: 1.6; }
    .back-link { color: #e2c98e; font-size: .9rem; font-weight: 650; text-decoration: none; }
    .back-link:hover { text-decoration: underline; }
    .header-icon {
      position: relative;
      z-index: 1;
      display: grid;
      place-items: center;
      width: 92px;
      height: 92px;
      border: 1px solid rgba(225, 198, 139, .45);
      border-radius: 24px;
      color: #e1c68b;
      font-size: 3.6rem;
      flex-shrink: 0;
    }

    .form-card, .periods-section {
      padding: 28px;
      border: 1px solid #e5e9e3;
      border-radius: 18px;
      background: #fff;
      box-shadow: 0 5px 20px rgba(25, 55, 38, .045);
    }

    .section-heading {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 16px;
      margin-bottom: 24px;
    }

    .section-heading h2 { margin: 7px 0 5px; color: #193c2c; font-size: 1.35rem; }
    .section-heading p { margin: 0; color: #78847b; font-size: .9rem; }
    .heading-icon {
      display: grid;
      place-items: center;
      width: 43px;
      height: 43px;
      border-radius: 13px;
      background: #f5f0e4;
      color: #98773c;
      font-size: 1.6rem;
    }

    .duty-form { display: grid; gap: 20px; }
    .form-field { display: grid; gap: 8px; }
    .form-field label { color: #344c3e; font-size: .88rem; font-weight: 700; }
    .form-field label span { color: #879087; font-weight: 400; }

    .form-field input, .form-field select {
      width: 100%;
      min-height: 48px;
      padding: 0 14px;
      border: 1px solid #dce3dc;
      border-radius: 10px;
      background: #fbfcfa;
      color: #253d30;
      font: inherit;
      outline: none;
    }

    .form-field input:focus, .form-field select:focus {
      border-color: #397653;
      box-shadow: 0 0 0 3px rgba(57, 118, 83, .12);
    }

    .date-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
    .form-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 18px;
      padding-top: 18px;
      border-top: 1px solid #edf0eb;
    }

    .form-footer p { max-width: 490px; margin: 0; color: #7b867e; font-size: .82rem; line-height: 1.5; }

    button { font: inherit; cursor: pointer; }
    .form-footer button {
      min-height: 46px;
      padding: 0 22px;
      border: 0;
      border-radius: 10px;
      background: #1d5038;
      color: #fff;
      font-weight: 750;
      white-space: nowrap;
      transition: background .2s, transform .2s;
    }

    .form-footer button:hover:not(:disabled) { background: #286747; transform: translateY(-1px); }
    button:disabled { opacity: .55; cursor: not-allowed; }

    .periods-heading { align-items: center; margin-bottom: 18px; }
    .count-badge {
      padding: 8px 12px;
      border-radius: 30px;
      background: #f5f0e4;
      color: #856b37;
      font-size: .78rem;
      font-weight: 750;
      white-space: nowrap;
    }

    .duty-list { display: grid; gap: 11px; }
    .duty-card {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 17px;
      border: 1px solid #e9ede8;
      border-radius: 13px;
      background: #fdfefc;
      transition: border-color .2s, transform .2s;
    }

    .duty-card:hover { border-color: #cbd9cc; transform: translateY(-1px); }
    .duty-symbol {
      display: grid;
      place-items: center;
      width: 44px;
      height: 44px;
      flex-shrink: 0;
      border-radius: 12px;
      background: #eaf2eb;
      color: #286243;
      font-size: 1.35rem;
    }

    .duty-info { display: grid; gap: 5px; flex: 1; min-width: 0; }
    .duty-info strong { color: #254332; font-size: .96rem; }
    .duty-dates { color: #77847a; font-size: .84rem; }

    .status {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      padding: 7px 10px;
      border-radius: 30px;
      font-size: .75rem;
      font-weight: 750;
      white-space: nowrap;
    }

    .status-dot { width: 7px; height: 7px; border-radius: 50%; background: currentColor; }
    .status-active { background: #e8f4eb; color: #287344; }
    .status-upcoming { background: #f8f1df; color: #95732e; }
    .status-finished { background: #eef0ef; color: #78817a; }

    .delete-button {
      padding: 9px 12px;
      border: 1px solid #f0d9d5;
      border-radius: 9px;
      background: #fff;
      color: #a34d42;
      font-size: .82rem;
      font-weight: 700;
      white-space: nowrap;
    }

    .delete-button:hover:not(:disabled) { background: #fff3f1; border-color: #e5bcb5; }

    .empty-state, .empty-periods, .loading-state {
      padding: 38px 20px;
      border: 1px dashed #d9e2d9;
      border-radius: 15px;
      background: #fff;
      text-align: center;
    }

    .empty-icon { color: #a18a56; font-size: 2rem; }
    .empty-state h2, .empty-periods h3 { margin: 10px 0 6px; color: #254332; }
    .empty-state p, .empty-periods p, .loading-state { color: #7a867d; font-size: .9rem; }

    .alert {
      display: flex;
      align-items: center;
      gap: 11px;
      padding: 14px 17px;
      border-radius: 11px;
      font-size: .9rem;
    }

    .alert > span { font-weight: 900; }
    .alert-error { border: 1px solid #f0d3ce; background: #fff4f2; color: #a33d32; }
    .alert-success { border: 1px solid #d3e7d7; background: #eff8f0; color: #286a3d; }

    .page-note {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      padding: 16px 19px;
      border: 1px solid #eee5d2;
      border-radius: 12px;
      background: #faf7ef;
      color: #806d45;
    }

    .page-note > span { font-size: 1.1rem; }
    .page-note p { margin: 0; font-size: .83rem; line-height: 1.6; }

    @media (max-width: 650px) {
      .duties-page { width: min(100% - 24px, 1100px); padding-top: 18px; gap: 17px; }
      .page-header { min-height: auto; padding: 25px 22px; }
      .header-icon { width: 57px; height: 57px; border-radius: 16px; font-size: 2.2rem; }
      .page-header p { font-size: .88rem; }
      .form-card, .periods-section { padding: 20px 16px; }
      .date-fields { grid-template-columns: 1fr; gap: 15px; }
      .form-footer { align-items: stretch; flex-direction: column; }
      .form-footer button { width: 100%; }
      .duty-card { flex-wrap: wrap; gap: 11px; }
      .duty-info { flex-basis: calc(100% - 60px); }
      .status { margin-left: 55px; }
      .delete-button { margin-left: auto; }
      .periods-heading { align-items: flex-start; }
    }
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
