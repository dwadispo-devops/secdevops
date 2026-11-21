import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { FamilyProfile, PrescriptionService } from '../services/prescription.service';

@Component({
  selector: 'app-family-profiles',
  standalone: true,
  imports: [FormsModule],
  template: `
    <main class="container">
      <h1>Profils de mes proches</h1>
      <p>Créez un libellé pour choisir la personne concernée lors du scan d’une ordonnance. Aucune date de naissance n’est demandée.</p>
      @if (error()) { <p role="alert">{{ error() }}</p> }
      @if (success()) { <p role="status">{{ success() }}</p> }
      <form (ngSubmit)="create()">
        <label for="new-label">Nom du profil</label>
        <input id="new-label" name="newLabel" [(ngModel)]="newLabel" maxlength="60" required />
        <button type="submit" [disabled]="busy()">Ajouter</button>
      </form>
      @if (profiles().length === 0 && !busy()) { <p>Aucun profil proche.</p> }
      @for (profile of profiles(); track profile.id) {
        <section class="profile">
          @if (editingId() === profile.id) {
            <form (ngSubmit)="rename(profile)">
              <label [for]="'label-' + profile.id">Libellé</label>
              <input [id]="'label-' + profile.id" name="editLabel" [(ngModel)]="editLabel" maxlength="60" required />
              <button type="submit" [disabled]="busy()">Enregistrer</button>
              <button type="button" (click)="editingId.set(null)">Annuler</button>
            </form>
          } @else {
            <strong>{{ profile.label }}</strong>
            <button type="button" (click)="startRename(profile)">Renommer</button>
            <button type="button" (click)="remove(profile)" [disabled]="busy()">Supprimer</button>
          }
        </section>
      }
    </main>
  `,
  styles: [`
    .container { max-width: 760px; margin: 40px auto; padding: 24px; }
    .profile, form { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin: 16px 0; }
    input { padding: 8px; }
    button { padding: 8px 12px; cursor: pointer; }
    [role="alert"] { color: #b00020; }
  `],
})
export class FamilyProfilesPage implements OnInit {
  private readonly service = inject(PrescriptionService);
  readonly profiles = signal<FamilyProfile[]>([]);
  newLabel = '';
  editLabel = '';
  readonly editingId = signal<string | null>(null);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  private get token(): string | null { return sessionStorage.getItem('access_token'); }

  ngOnInit(): void { this.load(); }

  create(): void {
    if (!this.token || this.busy()) return;
    const label = this.newLabel.trim();
    if (!label) { this.error.set('Saisissez un libellé.'); return; }
    this.busy.set(true); this.clearMessages();
    this.service.createFamilyProfile(label, this.token).subscribe({
      next: profile => { this.profiles.update(items => [...items, profile]); this.newLabel = ''; this.success.set('Profil ajouté.'); this.busy.set(false); },
      error: e => this.fail(e),
    });
  }

  startRename(profile: FamilyProfile): void { this.editingId.set(profile.id); this.editLabel = profile.label; }

  rename(profile: FamilyProfile): void {
    if (!this.token || this.busy()) return;
    const label = this.editLabel.trim();
    if (!label) { this.error.set('Saisissez un libellé.'); return; }
    this.busy.set(true); this.clearMessages();
    this.service.renameFamilyProfile(profile.id, label, this.token).subscribe({
      next: updated => { this.profiles.update(items => items.map(item => item.id === updated.id ? updated : item)); this.editingId.set(null); this.success.set('Profil renommé.'); this.busy.set(false); },
      error: e => this.fail(e),
    });
  }

  remove(profile: FamilyProfile): void {
    if (!this.token || this.busy() || !window.confirm(`Supprimer le profil « ${profile.label} » ?`)) return;
    this.busy.set(true); this.clearMessages();
    this.service.deleteFamilyProfile(profile.id, this.token).subscribe({
      next: () => { this.profiles.update(items => items.filter(item => item.id !== profile.id)); this.success.set('Profil supprimé des choix. Les ordonnances déjà envoyées gardent leur libellé.'); this.busy.set(false); },
      error: e => this.fail(e),
    });
  }

  private load(): void {
    if (!this.token) { this.error.set('Connectez-vous avec votre compte patient.'); return; }
    this.service.familyProfiles(this.token).subscribe({ next: p => this.profiles.set(p), error: e => this.fail(e) });
  }

  private fail(error: HttpErrorResponse): void {
    this.error.set(error.error?.detail || 'Impossible de modifier les profils.'); this.busy.set(false);
  }
  private clearMessages(): void { this.error.set(''); this.success.set(''); }
}
