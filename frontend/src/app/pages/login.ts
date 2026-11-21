import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../services/auth.service';

export interface LoginResponse { accessToken: string; refreshToken?: string; }


/** Retour autorisé uniquement vers une page interne. */
export function safeReturnUrl(value: string | null): string {
  return value && value.startsWith('/') && !value.startsWith('//') ? value : '/scan';
}

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink],
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
      <p class="switch">Vous êtes pharmacien ? <a routerLink="/pharmacien/connexion">Espace pharmacien</a></p>
    </main>
  `,
  styles: [`main { max-width: 480px; margin: 3rem auto; padding: 1.5rem; } form { display: grid; gap: .8rem; }
    input, button { padding: .75rem; font: inherit; } h1 { color: var(--couleur-bandeau); } .hint { color: #555; }
    button { background: var(--couleur-bandeau); color: #fff; border: 0; border-radius: 6px; cursor: pointer; }
    button:disabled { opacity: .6; cursor: default; } [role=alert] { color: #a11525; } a { color: var(--couleur-or-texte); }`],
})
export class LoginPage {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);

  email = '';
  password = '';
  readonly busy = signal(false);
  readonly error = signal('');

  login() {
    if (this.busy() || !this.email || !this.password) return;

    this.busy.set(true);
    this.error.set('');

    this.http.post<{ accessToken: string; refreshToken?: string }>(
      '/api/auth/login',
      { email: this.email.trim(), password: this.password },
    ).subscribe({
      next: result => {
        if (!result?.accessToken) {
          this.busy.set(false);
          this.error.set('Réponse d’authentification invalide.');
          return;
        }

        sessionStorage.setItem('access_token', result.accessToken);
        if (result.refreshToken) sessionStorage.setItem('refresh_token', result.refreshToken);

        this.auth.loadProfile().subscribe({
          next: profile => {
            this.busy.set(false);
            this.password = '';

            const requestedUrl = this.route.snapshot.queryParamMap.get('retour');
            const destination = requestedUrl
              ? safeReturnUrl(requestedUrl)
              : profile.role === 'PHARMACIEN'
                ? '/pharmacien/demandes'
                : profile.role === 'PATIENT'
                  ? '/scan'
                : profile.role === 'ADMIN'
                  ? '/admin/pharmacies'
                  : '/';

            void this.router.navigateByUrl(destination);
          },
          error: () => {
            this.auth.clearSession();
            this.busy.set(false);
            this.password = '';
            this.error.set('Impossible de récupérer votre profil. Réessayez.');
          },
        });
      },
      error: e => {
        this.busy.set(false);
        this.password = '';
        this.error.set(e.status === 401 ? 'Identifiants incorrects.'
          : e.status === 429 ? 'Trop de tentatives. Réessayez dans quelques minutes.'
          : 'Connexion impossible. Réessayez.');
      },
    });
  }
}
