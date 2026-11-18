import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

import { Profile, ProfileService } from '../services/profile.service';

/** Clés de session effacées après un changement de mot de passe ou une suppression de compte. */
const CLES_SESSION = ['access_token', 'refresh_token', 'last_prescription_id', 'demande_suivie'];

/** US20 / EF03 : consulter et modifier son profil, changer son mot de passe, supprimer son compte. */
@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <main class="profile">
      <h1>Mon profil</h1>

      @if (error()) { <p role="alert" class="error">{{ error() }}</p> }
      @if (success()) { <p role="status" class="success">{{ success() }}</p> }
      @if (loading()) { <p role="status">Chargement…</p> }

      @if (needsLogin()) {
        <p><a routerLink="/connexion">Se connecter</a> pour voir votre profil.</p>
      }

      @if (profile(); as p) {
        <section class="card" aria-labelledby="infos">
          <h2 id="infos">Mes informations</h2>
          <p><strong>E-mail :</strong> {{ p.email }}</p>
          <p><strong>Type de compte :</strong> {{ roleLabel(p.role) }}</p>
          <p><strong>Inscrit le :</strong> {{ format(p.createdAt) }}</p>

          <form (ngSubmit)="save()">
            <label>Prénom <input name="firstName" [(ngModel)]="firstName" required maxlength="100" /></label>
            <label>Nom <input name="lastName" [(ngModel)]="lastName" required maxlength="100" /></label>
            <button type="submit" [disabled]="sending()">Enregistrer</button>
          </form>
        </section>

        <section class="card" aria-labelledby="mdp">
          <h2 id="mdp">Changer mon mot de passe</h2>
          <p class="hint">Après le changement, vous serez déconnecté de tous vos appareils.</p>
          <form (ngSubmit)="changePassword()">
            <label>Mot de passe actuel
              <input type="password" name="current" [(ngModel)]="currentPassword" required autocomplete="current-password" />
            </label>
            <label>Nouveau mot de passe
              <input type="password" name="new" [(ngModel)]="newPassword" required autocomplete="new-password" />
            </label>
            <label>Confirmer le nouveau mot de passe
              <input type="password" name="confirm" [(ngModel)]="confirmPassword" required autocomplete="new-password" />
            </label>
            <button type="submit" [disabled]="sending()">Changer le mot de passe</button>
          </form>
        </section>

        <section class="card danger" aria-labelledby="suppr">
          <h2 id="suppr">Supprimer mon compte</h2>
          <p>
            Cette action est <strong>définitive</strong>. Seront effacés : votre compte,
            @if (p.role === 'PHARMACIEN') {
              vos notifications, et votre pharmacie sera détachée de votre compte.
            } @else {
              vos demandes, réservations, ordonnances (images comprises) et notifications.
            }
          </p>
          <form (ngSubmit)="deleteAccount()">
            <label>Mot de passe
              <input type="password" name="deletePassword" [(ngModel)]="deletePassword" required autocomplete="current-password" />
            </label>
            <label class="check">
              <input type="checkbox" name="confirmDelete" [(ngModel)]="confirmDelete" />
              Je comprends que cette action est définitive.
            </label>
            <button type="submit" class="danger-button" [disabled]="sending()">Supprimer définitivement mon compte</button>
          </form>
        </section>
      }
    </main>
  `,
  styles: [`
    .profile { max-width: 640px; margin: 2rem auto; padding: 0 1rem; display: grid; gap: 1rem; }
    .card { padding: 1rem 1.25rem; border: 1px solid var(--couleur-bordure); border-radius: 12px; display: grid; gap: .5rem; }
    .card.danger { border-color: #f09595; background: #fcebeb; }
    form { display: grid; gap: .75rem; }
    label { display: grid; gap: .25rem; }
    label.check { display: flex; align-items: center; gap: .5rem; }
    .danger-button { background: #a32d2d; color: #fff; border: none; padding: .6rem 1rem; border-radius: 8px; cursor: pointer; }
    .hint { color: #5f5e5a; font-size: .9rem; }
    .error { color: #a32d2d; }
    .success { color: var(--couleur-or-texte); }
  `],
})
export class ProfilePage implements OnInit {
  private readonly service = inject(ProfileService);
  private readonly router = inject(Router);

  readonly profile = signal<Profile | null>(null);
  readonly loading = signal(false);
  readonly sending = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  readonly needsLogin = signal(false);

  firstName = '';
  lastName = '';
  currentPassword = '';
  newPassword = '';
  confirmPassword = '';
  deletePassword = '';
  confirmDelete = false;

  ngOnInit(): void {
    this.loading.set(true);
    this.service.get().subscribe({
      next: (p) => {
        this.profile.set(p);
        this.firstName = p.firstName;
        this.lastName = p.lastName;
        this.loading.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.loading.set(false);
        if (e.status === 401) this.needsLogin.set(true);
        else this.error.set(this.message(e));
      },
    });
  }

  save(): void {
    this.reset();
    if (!this.firstName.trim() || !this.lastName.trim()) {
      this.error.set('Le prénom et le nom sont obligatoires.');
      return;
    }
    this.sending.set(true);
    this.service.update(this.firstName, this.lastName).subscribe({
      next: (p) => {
        this.profile.set(p);
        this.firstName = p.firstName;
        this.lastName = p.lastName;
        this.success.set('Profil mis à jour.');
        this.sending.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.error.set(this.message(e));
        this.sending.set(false);
      },
    });
  }

  changePassword(): void {
    this.reset();
    if (this.newPassword !== this.confirmPassword) {
      this.error.set('Les deux nouveaux mots de passe ne correspondent pas.');
      return;
    }
    this.sending.set(true);
    this.service.changePassword(this.currentPassword, this.newPassword).subscribe({
      next: () => {
        this.clearSession();
        this.sending.set(false);
        this.router.navigate(['/connexion'], { queryParams: { motDePasse: 'modifie' } });
      },
      error: (e: HttpErrorResponse) => {
        this.error.set(this.message(e));
        this.sending.set(false);
      },
    });
  }

  /** US20 / loi 09-08 : suppression définitive, confirmée par le mot de passe et la case à cocher. */
  deleteAccount(): void {
    this.reset();
    if (!this.confirmDelete) {
      this.error.set('Cochez la case pour confirmer que vous comprenez que la suppression est définitive.');
      return;
    }
    if (!this.deletePassword) {
      this.error.set('Saisissez votre mot de passe pour confirmer.');
      return;
    }
    this.sending.set(true);
    this.service.deleteAccount(this.deletePassword).subscribe({
      next: () => {
        this.clearSession();
        this.sending.set(false);
        this.router.navigate(['/'], { queryParams: { compte: 'supprime' } });
      },
      error: (e: HttpErrorResponse) => {
        this.error.set(this.message(e));
        this.sending.set(false);
      },
    });
  }

  roleLabel(role: Profile['role']): string {
    return role === 'PHARMACIEN' ? 'Pharmacien' : role === 'ADMIN' ? 'Administrateur' : 'Patient';
  }

  format(iso: string): string {
    return new Date(iso).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' });
  }

  private clearSession(): void {
    CLES_SESSION.forEach((cle) => sessionStorage.removeItem(cle));
  }

  private reset(): void {
    this.error.set('');
    this.success.set('');
  }

  private message(e: HttpErrorResponse): string {
    const champs = e.error?.errors ? Object.values(e.error.errors as Record<string, string>) : [];
    if (champs.length) return champs.join(' ');
    if (e.error?.detail) return e.error.detail;
    if (e.status === 401) return 'Votre session a expiré : reconnectez-vous.';
    return 'Le serveur ne répond pas. Réessayez dans un instant.';
  }
}
