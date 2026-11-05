import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';
import { LoginResponse } from './login';

/** Même règle que le back-end (RegisterRequest) : 8 à 72 caractères, au moins une lettre et un chiffre. */
export const PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/;

@Component({
  selector: 'app-register', imports: [FormsModule, RouterLink],
  template: `
    <main>
      <h1>Créer un compte patient</h1>
      <form (ngSubmit)="register()">
        <label for="firstName">Prénom</label>
        <input id="firstName" name="firstName" autocomplete="given-name" maxlength="100" [(ngModel)]="firstName" required />
        <label for="lastName">Nom</label>
        <input id="lastName" name="lastName" autocomplete="family-name" maxlength="100" [(ngModel)]="lastName" required />
        <label for="email">Adresse e-mail</label>
        <input id="email" name="email" type="email" autocomplete="email" maxlength="255" [(ngModel)]="email" required />
        <label for="password">Mot de passe</label>
        <input id="password" name="password" type="password" autocomplete="new-password" maxlength="72" [(ngModel)]="password" required />
        <small [class.ko]="password && !passwordValid()">8 caractères minimum, avec au moins une lettre et un chiffre.</small>
        <label for="confirm">Confirmer le mot de passe</label>
        <input id="confirm" name="confirm" type="password" autocomplete="new-password" [(ngModel)]="confirm" required />
        @if (confirm && confirm !== password) { <small class="ko">Les mots de passe ne correspondent pas.</small> }
        <button type="submit" [disabled]="!canSubmit()">{{ busy() ? 'Création…' : 'Créer mon compte' }}</button>
      </form>
      @if (error()) { <p role="alert">{{ error() }}</p> }
      <p class="switch">Déjà inscrit ? <a routerLink="/connexion">Se connecter</a></p>
    </main>
  `,
  styles: [`main { max-width: 480px; margin: 3rem auto; padding: 1.5rem; } form { display: grid; gap: .6rem; }
    input, button { padding: .75rem; font: inherit; } h1 { color: #176b55; } small { color: #555; } .ko { color: #a11525; }
    button { margin-top: .6rem; background: #176b55; color: #fff; border: 0; border-radius: 6px; cursor: pointer; }
    button:disabled { opacity: .6; cursor: default; } [role=alert] { color: #a11525; } a { color: #176b55; }`],
})
export class RegisterPage {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  firstName = ''; lastName = ''; email = ''; password = ''; confirm = '';
  readonly busy = signal(false); readonly error = signal('');

  passwordValid() { return PASSWORD_RULE.test(this.password); }
  canSubmit() {
    return !this.busy() && !!this.firstName.trim() && !!this.lastName.trim() && !!this.email.trim()
      && this.passwordValid() && this.password === this.confirm;
  }

  register() {
    if (!this.canSubmit()) return;
    this.busy.set(true); this.error.set('');
    const email = this.email.trim();
    const body = { email, password: this.password, firstName: this.firstName.trim(), lastName: this.lastName.trim() };
    this.http.post('/api/auth/register', body).pipe(
      switchMap(() => this.http.post<LoginResponse>('/api/auth/login', { email, password: body.password })),
    ).subscribe({
      next: result => {
        this.busy.set(false); this.password = ''; this.confirm = '';
        sessionStorage.setItem('access_token', result.accessToken);
        if (result.refreshToken) sessionStorage.setItem('refresh_token', result.refreshToken);
        void this.router.navigateByUrl('/scan');
      },
      error: e => {
        this.busy.set(false);
        this.error.set(e.status === 409 ? 'Un compte existe déjà avec cette adresse e-mail.'
          : e.status === 400 ? (e.error?.message ?? 'Certaines informations sont invalides.')
          : 'Inscription impossible. Réessayez.');
      },
    });
  }
}
