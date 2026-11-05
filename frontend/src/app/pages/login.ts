import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

export interface LoginResponse { accessToken: string; refreshToken?: string; }

/** Retour autorisé uniquement vers une page interne (évite une redirection ouverte). */
export function safeReturnUrl(value: string | null): string {
  return value && value.startsWith('/') && !value.startsWith('//') ? value : '/scan';
}

@Component({
  selector: 'app-login', imports: [FormsModule, RouterLink],
  template: `
    <main>
      <h1>Connexion</h1>
      <p class="hint">Connectez-vous pour scanner une ordonnance et suivre vos demandes.</p>
      <form (ngSubmit)="login()">
        <label for="email">Adresse e-mail</label>
        <input id="email" name="email" type="email" autocomplete="username" [(ngModel)]="email" required />
        <label for="password">Mot de passe</label>
        <input id="password" name="password" type="password" autocomplete="current-password" [(ngModel)]="password" required />
        <button type="submit" [disabled]="busy() || !email || !password">{{ busy() ? 'Connexion…' : 'Se connecter' }}</button>
      </form>
      @if (error()) { <p role="alert">{{ error() }}</p> }
      <p class="switch">Pas encore de compte ? <a routerLink="/inscription">Créer un compte</a></p>
    </main>
  `,
  styles: [`main { max-width: 480px; margin: 3rem auto; padding: 1.5rem; } form { display: grid; gap: .8rem; }
    input, button { padding: .75rem; font: inherit; } h1 { color: #176b55; } .hint { color: #555; }
    button { background: #176b55; color: #fff; border: 0; border-radius: 6px; cursor: pointer; }
    button:disabled { opacity: .6; cursor: default; } [role=alert] { color: #a11525; } a { color: #176b55; }`],
})
export class LoginPage {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  email = ''; password = '';
  readonly busy = signal(false); readonly error = signal('');

  login() {
    if (this.busy() || !this.email || !this.password) return;
    this.busy.set(true); this.error.set('');
    this.http.post<LoginResponse>('/api/auth/login', { email: this.email.trim(), password: this.password }).subscribe({
      next: result => {
        this.busy.set(false); this.password = '';
        if (!result?.accessToken) { this.error.set('Réponse d’authentification invalide.'); return; }
        sessionStorage.setItem('access_token', result.accessToken);
        if (result.refreshToken) sessionStorage.setItem('refresh_token', result.refreshToken);
        void this.router.navigateByUrl(safeReturnUrl(this.route.snapshot.queryParamMap.get('retour')));
      },
      error: e => {
        this.busy.set(false); this.password = '';
        this.error.set(e.status === 401 ? 'Identifiants incorrects.'
          : e.status === 429 ? 'Trop de tentatives. Réessayez dans quelques minutes.'
          : 'Connexion impossible. Réessayez.');
      },
    });
  }
}
