import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-pharmacist-login',
  imports: [FormsModule, RouterLink],
  template: `
    <main>
      <h1>Connexion à l’espace pharmacien</h1>
      <form (ngSubmit)="login()">
        <label for="email">Adresse e-mail</label>
        <input id="email" name="email" type="email" autocomplete="username" [(ngModel)]="email" required />
        <label for="password">Mot de passe</label>
        <input id="password" name="password" type="password" autocomplete="current-password" [(ngModel)]="password" required />
        <button type="submit" [disabled]="busy() || !email || !password">
          {{ busy() ? 'Connexion…' : 'Se connecter' }}
        </button>
      </form>
      @if (error()) { <p role="alert">{{ error() }}</p> }
      <p>Nouvelle pharmacie ? <a routerLink="/pharmacien/inscription">Inscrire ma pharmacie</a></p>
      <p>Vous êtes patient ? <a routerLink="/connexion">Connexion patient</a></p>
    </main>
  `,
  styles: [`main { max-width: 480px; margin: 3rem auto; padding: 1.5rem; } form { display: grid; gap: .8rem; }
    input, button { padding: .75rem; font: inherit; } h1 { color: var(--couleur-bandeau); }
    [role=alert] { color: #a11525; } a { color: var(--couleur-or-texte); }`],
})
export class PharmacistLoginPage {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
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

            if (profile.role !== 'PHARMACIEN') {
              this.auth.clearSession();
              this.error.set('Ce compte ne possède pas un accès pharmacien. Utilisez la connexion patient.');
              return;
            }

            void this.router.navigateByUrl('/pharmacien/demandes');
          },
          error: () => {
            this.auth.clearSession();
            this.busy.set(false);
            this.password = '';
            this.error.set('Impossible de vérifier votre profil. Réessayez.');
          },
        });
      },
      error: e => {
        this.busy.set(false);
        this.password = '';
        this.error.set(e.status === 401
          ? 'Identifiants incorrects.'
          : 'Connexion impossible. Réessayez.');
      },
    });
  }
}
