import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import {
  MAX_FILE_SIZE,
  PrescriptionService,
  PrescriptionUploadResponse,
  isAcceptedFile,
  isImage,
  isPdf,
  prepareImage,
} from '../services/prescription.service';

/**
 * US06 – En tant que patient, je veux photographier ou importer mon ordonnance.
 * RG05 : JPG / PNG / PDF, 5 Mo max (revérifié par le serveur).
 * RG06 : consentement obligatoire avant l'envoi.
 */
@Component({
  selector: 'app-scan',
  standalone: true,
  template: `
    <main class="container">
      <h1>Scanner une ordonnance</h1>

      @if (result(); as r) {
        <section class="success" role="status">
          <h2>✅ Ordonnance envoyée</h2>
          <p>Votre ordonnance a bien été reçue et enregistrée de façon sécurisée.</p>
          <p class="meta">Référence : {{ r.id }}</p>
          <button type="button" (click)="restart()">Envoyer une autre ordonnance</button>
        </section>
      } @else {
        <p>Prenez votre ordonnance en photo ou importez-la (JPG, PNG ou PDF, 5 Mo maximum).</p>

        <div class="choices">
          <button type="button" (click)="cameraInput.click()" [disabled]="busy()">📷 Prendre une photo</button>
          <button type="button" (click)="fileInput.click()" [disabled]="busy()">📁 Importer un fichier</button>
        </div>

        <!-- accept="image/*" : sur téléphone, propose "Appareil photo" ou "Galerie" ; sur PC, ouvre le sélecteur -->
        <input #cameraInput type="file" accept="image/*" hidden
               data-testid="camera-input" (change)="onFileSelected($event)" />
        <input #fileInput type="file" accept=".jpg,.jpeg,.png,.pdf,image/jpeg,image/png,application/pdf" hidden
               data-testid="file-input" (change)="onFileSelected($event)" />

        @if (preparing()) {
          <p role="status">Préparation de l'image…</p>
        }

        @if (file(); as f) {
          <section class="preview">
            @if (previewUrl(); as url) {
              <img [src]="url" alt="Aperçu de votre ordonnance" />
            } @else {
              <p class="pdf">📄 Document PDF</p>
            }
            <p class="meta">{{ f.name }} – {{ formatSize(f.size) }}</p>
            <button type="button" class="link" (click)="clear()" [disabled]="loading()">Changer de fichier</button>
          </section>

          <label class="consent">
            <input type="checkbox" data-testid="consent"
                   [checked]="consent()" (change)="onConsentChange($event)" [disabled]="loading()" />
            <span>
              J'accepte que l'image de mon ordonnance soit envoyée à la plateforme et lue par une
              intelligence artificielle. Elle est conservée chiffrée uniquement le temps de ma demande,
              puis supprimée.
            </span>
          </label>

          <button type="button" class="primary" data-testid="submit" (click)="submit()" [disabled]="!canSubmit()">
            {{ loading() ? 'Envoi en cours…' : 'Envoyer mon ordonnance' }}
          </button>
        }
      }

      @if (error()) {
        <p class="error" role="alert">{{ error() }}</p>
      }
    </main>
  `,
  styles: [`
    .container { max-width: 760px; margin: 40px auto; padding: 24px; }
    h1 { color: #176b55; }
    .choices { display: flex; gap: 12px; flex-wrap: wrap; margin: 20px 0; }
    .choices button { flex: 1 1 200px; padding: 16px; font-size: 1rem; }
    button { padding: 10px 16px; cursor: pointer; }
    button:disabled { cursor: not-allowed; opacity: 0.6; }
    .preview { margin: 20px 0; padding: 16px; border: 1px solid #d0d7d4; border-radius: 8px; text-align: center; }
    .preview img { max-width: 100%; max-height: 420px; border-radius: 4px; }
    .pdf { font-size: 1.2rem; }
    .meta { color: #555; font-size: 0.9rem; word-break: break-all; }
    .link { background: none; border: none; color: #176b55; text-decoration: underline; }
    .consent { display: flex; gap: 10px; align-items: flex-start; margin: 20px 0; line-height: 1.4; }
    .consent input { margin-top: 4px; width: 20px; height: 20px; flex-shrink: 0; }
    .primary { width: 100%; padding: 14px; font-size: 1rem; background: #176b55; color: #fff; border: none; border-radius: 6px; }
    .success { padding: 20px; border-radius: 8px; background: #e8f5ef; }
    .error { color: #b00020; font-weight: 600; }
  `],
})
export class ScanPage implements OnDestroy {
  private readonly prescriptionService = inject(PrescriptionService);

  readonly file = signal<File | null>(null);
  readonly previewUrl = signal<string | null>(null);
  readonly consent = signal(false);
  readonly preparing = signal(false);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly result = signal<PrescriptionUploadResponse | null>(null);

  readonly busy = computed(() => this.preparing() || this.loading());
  readonly canSubmit = computed(() => this.file() !== null && this.consent() && !this.busy());

  async onFileSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const selected = input.files?.[0];
    input.value = ''; // permet de re-choisir le même fichier
    if (!selected) {
      return;
    }

    this.error.set('');
    if (!isPdf(selected) && !isImage(selected)) {
      this.error.set('Format non accepté. Formats autorisés : JPG, PNG ou PDF.');
      return;
    }

    this.preparing.set(true);
    const prepared = await prepareImage(selected);
    this.preparing.set(false);

    if (!isAcceptedFile(prepared)) {
      this.error.set('Format non accepté. Formats autorisés : JPG, PNG ou PDF.');
      return;
    }
    if (prepared.size === 0) {
      this.error.set('Le fichier est vide.');
      return;
    }
    if (prepared.size > MAX_FILE_SIZE) {
      this.error.set('Le fichier dépasse la taille maximale de 5 Mo.');
      return;
    }
    this.setFile(prepared);
  }

  onConsentChange(event: Event): void {
    this.consent.set((event.target as HTMLInputElement).checked);
  }

  submit(): void {
    const file = this.file();
    if (!file || !this.canSubmit()) {
      return;
    }

    const token = sessionStorage.getItem('access_token');
    if (!token) {
      this.error.set('Connectez-vous avec un compte patient pour envoyer votre ordonnance.');
      return;
    }

    this.loading.set(true);
    this.error.set('');

    this.prescriptionService.upload(file, true, token).subscribe({
      next: response => {
        this.loading.set(false);
        // Identifiant réutilisable par l'étape suivante (lecture IA, US07)
        sessionStorage.setItem('last_prescription_id', response.id);
        this.clear();
        this.result.set(response);
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(this.errorMessage(error));
      },
    });
  }

  clear(): void {
    this.revokePreview();
    this.file.set(null);
    this.consent.set(false);
  }

  restart(): void {
    this.clear();
    this.result.set(null);
    this.error.set('');
  }

  formatSize(bytes: number): string {
    return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} Ko` : `${(bytes / (1024 * 1024)).toFixed(1)} Mo`;
  }

  ngOnDestroy(): void {
    this.revokePreview();
  }

  private setFile(file: File): void {
    this.revokePreview();
    this.file.set(file);
    this.previewUrl.set(isPdf(file) ? null : URL.createObjectURL(file));
  }

  private revokePreview(): void {
    const url = this.previewUrl();
    if (url) {
      URL.revokeObjectURL(url);
    }
    this.previewUrl.set(null);
  }

  private errorMessage(error: HttpErrorResponse): string {
    switch (error.status) {
      case 0:
        return 'Impossible de joindre le serveur. Vérifiez votre connexion.';
      case 401:
        return 'Session invalide ou expirée. Reconnectez-vous.';
      case 403:
        return 'Seul un compte patient peut envoyer une ordonnance.';
      case 413:
        return 'Le fichier dépasse la taille maximale de 5 Mo.';
    }
    // Les autres erreurs du serveur ont déjà un message en français (champ "detail")
    const detail = error.error?.detail;
    return typeof detail === 'string' && detail ? detail : 'Échec de l’envoi. Réessayez dans un instant.';
  }
}
