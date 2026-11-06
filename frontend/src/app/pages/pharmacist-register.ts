import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { Observable, map, of, switchMap } from 'rxjs';
import { PASSWORD_RULE } from './register';
import { LoginResponse } from './login';

export interface PharmacieInscrite { id: string; name: string; status: string; }

/** US03 / EF05 – Le pharmacien crée son compte puis inscrit sa pharmacie (validée ensuite par un administrateur, EF06). */
@Component({
  selector: 'app-pharmacist-register', imports: [FormsModule, RouterLink],
  template: `
    <main>
      <h1>Inscrire ma pharmacie</h1>
      @if (resultat(); as p) {
        <section class="succes" role="status">
          <h2>✅ Pharmacie enregistrée</h2>
          <p><strong>{{ p.name }}</strong> est <strong>en attente de validation</strong> par un administrateur.
             Vous recevrez les demandes des patients dès qu’elle sera validée.</p>
          <a routerLink="/pharmacien/demandes">Accéder à mon espace pharmacien</a>
        </section>
      } @else {
        <form (ngSubmit)="inscrire()">
          @if (!compteCree()) {
            <fieldset>
              <legend>1. Votre compte pharmacien</legend>
              <label for="firstName">Prénom</label>
              <input id="firstName" name="firstName" autocomplete="given-name" maxlength="100" [(ngModel)]="firstName" required />
              <label for="lastName">Nom</label>
              <input id="lastName" name="lastName" autocomplete="family-name" maxlength="100" [(ngModel)]="lastName" required />
              <label for="email">Adresse e-mail professionnelle</label>
              <input id="email" name="email" type="email" autocomplete="email" maxlength="255" [(ngModel)]="email" required />
              <label for="password">Mot de passe</label>
              <input id="password" name="password" type="password" autocomplete="new-password" maxlength="72" [(ngModel)]="password" required />
              <small [class.ko]="password && !passwordValid()">8 caractères minimum, avec au moins une lettre et un chiffre.</small>
              <label for="confirm">Confirmer le mot de passe</label>
              <input id="confirm" name="confirm" type="password" autocomplete="new-password" [(ngModel)]="confirm" required />
              @if (confirm && confirm !== password) { <small class="ko">Les mots de passe ne correspondent pas.</small> }
            </fieldset>
          } @else {
            <p class="ok">✔ Compte pharmacien créé. Complétez les informations de la pharmacie.</p>
          }

          <fieldset>
            <legend>2. Votre pharmacie</legend>
            <label for="name">Nom de la pharmacie</label>
            <input id="name" name="name" maxlength="200" [(ngModel)]="name" required />
            <label for="license">N° d’autorisation d’exercice</label>
            <input id="license" name="license" maxlength="100" [(ngModel)]="licenseNumber" required />
            <label for="address">Adresse</label>
            <input id="address" name="address" autocomplete="street-address" maxlength="300" [(ngModel)]="address" required />
            <label for="phone">Téléphone (facultatif)</label>
            <input id="phone" name="phone" type="tel" maxlength="30" [(ngModel)]="phone" />
            <label for="hours">Horaires (facultatif)</label>
            <input id="hours" name="hours" maxlength="200" placeholder="Lun-Sam 9h-20h" [(ngModel)]="openingHours" />
            <label>Position de la pharmacie</label>
            <div class="position">
              <button type="button" (click)="localiser()" [disabled]="locating()">{{ locating() ? 'Localisation…' : '📍 Utiliser ma position' }}</button>
              <input name="latitude" type="number" step="any" placeholder="Latitude" aria-label="Latitude"
                     [ngModel]="latitude()" (ngModelChange)="latitude.set($event)" />
              <input name="longitude" type="number" step="any" placeholder="Longitude" aria-label="Longitude"
                     [ngModel]="longitude()" (ngModelChange)="longitude.set($event)" />
            </div>
            <small>Placez-vous dans la pharmacie et cliquez sur « Utiliser ma position », ou saisissez ses coordonnées.</small>
          </fieldset>

          <button type="submit" class="primaire" [disabled]="!canSubmit()">{{ busy() ? 'Inscription…' : 'Inscrire ma pharmacie' }}</button>
        </form>
        @if (error()) { <p role="alert">{{ error() }}</p> }
        <p class="switch">Déjà inscrit ? <a routerLink="/pharmacien/connexion">Se connecter</a></p>
      }
    </main>
  `,
  styles: [`main { max-width: 560px; margin: 2.5rem auto; padding: 1.5rem; } h1 { color: #176b55; }
    form { display: grid; gap: 1rem; } fieldset { display: grid; gap: .5rem; border: 1px solid #dfe6e3; border-radius: 10px; padding: 1rem 1.2rem; }
    legend { font-weight: 700; color: #176b55; padding: 0 .3rem; } input, button { padding: .7rem; font: inherit; }
    small { color: #555; } .ko, [role=alert] { color: #a11525; } .ok { color: #166534; font-weight: 600; }
    .position { display: grid; grid-template-columns: auto 1fr 1fr; gap: .5rem; }
    @media (max-width: 520px) { .position { grid-template-columns: 1fr; } }
    .primaire { background: #176b55; color: #fff; border: 0; border-radius: 6px; cursor: pointer; }
    .primaire:disabled { opacity: .55; cursor: default; } a { color: #176b55; }
    .succes { padding: 1.2rem 1.5rem; border-radius: 12px; background: #e8f5ef; border: 2px solid #16a34a; }`],
})
export class PharmacistRegisterPage {
  private readonly http = inject(HttpClient);
  firstName = ''; lastName = ''; email = ''; password = ''; confirm = '';
  name = ''; licenseNumber = ''; address = ''; phone = ''; openingHours = '';
  readonly latitude = signal<number | null>(null);
  readonly longitude = signal<number | null>(null);
  readonly busy = signal(false);
  readonly locating = signal(false);
  readonly error = signal('');
  /** Le compte existe déjà (ex. : la pharmacie a été refusée) : on ne refait que l'étape 2. */
  readonly compteCree = signal(false);
  readonly resultat = signal<PharmacieInscrite | null>(null);

  passwordValid() { return PASSWORD_RULE.test(this.password); }
  compteValide() {
    return this.compteCree() || (!!this.firstName.trim() && !!this.lastName.trim() && !!this.email.trim()
      && this.passwordValid() && this.password === this.confirm);
  }
  pharmacieValide() {
    const lat = this.latitude(), lng = this.longitude();
    return !!this.name.trim() && !!this.licenseNumber.trim() && !!this.address.trim()
      && lat !== null && lng !== null && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
  }
  canSubmit() { return !this.busy() && this.compteValide() && this.pharmacieValide(); }

  localiser(): void {
    if (!navigator.geolocation) { this.error.set('La géolocalisation n’est pas disponible : saisissez les coordonnées.'); return; }
    this.locating.set(true);
    navigator.geolocation.getCurrentPosition(
      p => { this.latitude.set(+p.coords.latitude.toFixed(6)); this.longitude.set(+p.coords.longitude.toFixed(6)); this.locating.set(false); },
      () => { this.locating.set(false); this.error.set('Position indisponible : saisissez la latitude et la longitude.'); },
      { enableHighAccuracy: true, timeout: 20000 });
  }

  inscrire(): void {
    if (!this.canSubmit()) return;
    this.busy.set(true); this.error.set('');
    const email = this.email.trim();
    const jeton$: Observable<string> = this.compteCree()
      ? of(sessionStorage.getItem('access_token') ?? '')
      : this.http.post('/api/auth/register/pharmacien',
          { email, password: this.password, firstName: this.firstName.trim(), lastName: this.lastName.trim() }).pipe(
          switchMap(() => this.http.post<LoginResponse>('/api/auth/login', { email, password: this.password })),
          map(r => {
            sessionStorage.setItem('access_token', r.accessToken);
            if (r.refreshToken) sessionStorage.setItem('refresh_token', r.refreshToken);
            this.compteCree.set(true); this.password = ''; this.confirm = '';
            return r.accessToken;
          }));
    jeton$.pipe(switchMap(token => this.http.post<PharmacieInscrite>('/api/pharmacies', {
      name: this.name.trim(), licenseNumber: this.licenseNumber.trim(), address: this.address.trim(),
      phone: this.phone.trim() || null, openingHours: this.openingHours.trim() || null,
      latitude: this.latitude(), longitude: this.longitude(),
    }, { headers: { Authorization: `Bearer ${token}` } }))).subscribe({
      next: p => { this.busy.set(false); this.resultat.set(p); },
      error: (e: HttpErrorResponse) => { this.busy.set(false); this.error.set(this.message(e)); },
    });
  }

  private message(e: HttpErrorResponse): string {
    if (e.status === 409) return this.compteCree()
      ? 'Ce numéro d’autorisation est déjà enregistré pour une autre pharmacie.'
      : 'Un compte existe déjà avec cette adresse e-mail.';
    if (e.status === 400) return 'Certaines informations sont invalides. Vérifiez les champs.';
    if (e.status === 403) return 'Ce compte n’a pas le rôle pharmacien.';
    return 'Inscription impossible pour le moment. Réessayez.';
  }
}
