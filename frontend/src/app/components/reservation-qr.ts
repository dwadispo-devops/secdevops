import { Component, computed, input } from '@angular/core';
import { create } from 'qrcode';

/** Encodage local : seul le code de retrait entre dans le QR, aucun appel réseau. */
@Component({
  selector: 'app-reservation-qr',
  template: `
    @if (qr(); as image) {
      <svg xmlns="http://www.w3.org/2000/svg" role="img" aria-label="QR code de retrait"
           [attr.viewBox]="'0 0 ' + image.size + ' ' + image.size" width="232" height="232"
           shape-rendering="crispEdges">
        <rect width="100%" height="100%" fill="#fff" />
        <path [attr.d]="image.path" fill="#000" />
      </svg>
    } @else {
      <p role="status">QR code indisponible. Utilisez le code texte au comptoir.</p>
    }
  `,
  styles: ['svg { display: block; max-width: 100%; height: auto; margin: .75rem 0; }'],
})
export class ReservationQrComponent {
  readonly code = input.required<string>();
  readonly qr = computed(() => {
    const code = this.code();
    if (!/^[A-HJ-NP-Z2-9]{6}$/.test(code)) return null;
    try {
      const { modules } = create(code, { errorCorrectionLevel: 'M' });
      // Quatre modules blancs autour du symbole pour faciliter sa lecture.
      let path = '';
      for (let y = 0; y < modules.size; y++) {
        for (let x = 0; x < modules.size; x++) {
          if (modules.get(y, x)) path += `M${x + 4} ${y + 4}h1v1h-1z`;
        }
      }
      return { size: modules.size + 8, path };
    } catch {
      return null;
    }
  });
}
