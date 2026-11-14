import { TestBed } from '@angular/core/testing';
import jsQR from 'jsqr';
import { ReservationQrComponent } from './reservation-qr';

describe('ReservationQrComponent', () => {
  it('produit un SVG décodable contenant exclusivement le code de retrait', () => {
    const fixture = TestBed.createComponent(ReservationQrComponent);
    fixture.componentRef.setInput('code', 'K7P2QX');
    fixture.detectChanges();
    const svg: SVGSVGElement = fixture.nativeElement.querySelector('svg');
    expect(svg.getAttribute('aria-label')).toBe('QR code de retrait');
    // Reconstruire les pixels du SVG réellement rendu puis utiliser un décodeur indépendant.
    const size = Number(svg.getAttribute('viewBox')!.split(' ')[2]);
    const scale = 8;
    const width = size * scale;
    const pixels = new Uint8ClampedArray(width * width * 4).fill(255);
    const path = svg.querySelector('path')!.getAttribute('d')!;
    for (const match of path.matchAll(/M(\d+) (\d+)h1v1h-1z/g)) {
      const x = Number(match[1]) * scale, y = Number(match[2]) * scale;
      for (let dy = 0; dy < scale; dy++) for (let dx = 0; dx < scale; dx++) {
        const i = ((y + dy) * width + x + dx) * 4;
        pixels[i] = pixels[i + 1] = pixels[i + 2] = 0;
      }
    }
    expect(jsQR(pixels, width, width)?.data).toBe('K7P2QX');
    expect(svg.querySelector('image')).toBeNull();
  });

  it('actualise le symbole lorsque le code change', () => {
    const fixture = TestBed.createComponent(ReservationQrComponent);
    fixture.componentRef.setInput('code', 'K7P2QX');
    fixture.detectChanges();
    const before = fixture.nativeElement.querySelector('path').getAttribute('d');
    fixture.componentRef.setInput('code', 'ABC234');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('path').getAttribute('d')).not.toBe(before);
  });

  it('conserve une alternative accessible si le code est invalide', () => {
    const fixture = TestBed.createComponent(ReservationQrComponent);
    fixture.componentRef.setInput('code', 'patient@example.com');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('svg')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Utilisez le code texte');
  });
});
