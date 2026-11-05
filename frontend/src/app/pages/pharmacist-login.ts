import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';

@Component({
  selector: 'app-pharmacist-login', imports: [FormsModule],
  template: `
    <main>
      <h1>Connexion à l’espace pharmacien</h1>
      <form (ngSubmit)="login()">
        <label for="email">Adresse e-mail</label>
        <input id="email" name="email" type="email" autocomplete="username" [(ngModel)]="email" required />
        <label for="password">Mot de passe</label>
        <input id="password" name="password" type="password" autocomplete="current-password" [(ngModel)]="password" required />
        <button type="submit" [disabled]="busy() || !email || !password">Se connecter</button>
      </form>
      @if (error()) { <p role="alert">{{ error() }}</p> }
    </main>
  `,
  styles: [`main { max-width: 480px; margin: 3rem auto; padding: 1.5rem; } form { display: grid; gap: .8rem; }
    input, button { padding: .75rem; font: inherit; } h1 { color: #176b55; } [role=alert] { color: #a11525; }`],
})
export class PharmacistLoginPage {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  email = ''; password = '';
  readonly busy = signal(false); readonly error = signal('');
  login() {
    if (this.busy() || !this.email || !this.password) return;
    this.busy.set(true); this.error.set('');
    this.http.post<{ accessToken: string }>('/api/auth/login', { email: this.email.trim(), password: this.password }).subscribe({
      next: result => {
        this.busy.set(false); this.password = '';
        if (!result.accessToken) { this.error.set('Réponse d’authentification invalide.'); return; }
        sessionStorage.setItem('access_token', result.accessToken);
        void this.router.navigateByUrl('/pharmacien/demandes');
      },
      error: e => { this.busy.set(false); this.password = ''; this.error.set(e.status === 401
        ? 'Identifiants incorrects.' : 'Connexion impossible. Réessayez.'); },
    });
  }
}
