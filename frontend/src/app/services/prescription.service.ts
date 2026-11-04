import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/** Réponse de POST /api/v1/prescriptions (US06). */
export interface PrescriptionUploadResponse {
  id: string;
  status: string;
  fileType: string;
  sizeBytes: number;
  createdAt: string;
}

/** RG05 : 5 Mo maximum, JPG / PNG / PDF. */
export const MAX_FILE_SIZE = 5 * 1024 * 1024;
export const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'application/pdf'];
const ACCEPTED_EXTENSIONS = ['jpg', 'jpeg', 'png', 'pdf'];

/** Côté le plus long de la photo après réduction (lisible par l'IA, plus léger à envoyer). */
const MAX_IMAGE_SIDE = 2000;
const JPEG_QUALITY = 0.85;

@Injectable({
  providedIn: 'root',
})
export class PrescriptionService {
  private readonly http = inject(HttpClient);

  private readonly apiUrl = '/api/v1/prescriptions';

  /** Envoie l'ordonnance (multipart : file + consent) avec le jeton du patient. */
  upload(file: File, consent: boolean, token: string): Observable<PrescriptionUploadResponse> {
    const form = new FormData();
    form.append('file', file, file.name);
    form.append('consent', String(consent));

    return this.http.post<PrescriptionUploadResponse>(this.apiUrl, form, {
      headers: { Authorization: `Bearer ${token}` },
    });
  }
}

export function extensionOf(name: string): string {
  const dot = name.lastIndexOf('.');
  return dot < 0 ? '' : name.substring(dot + 1).toLowerCase();
}

export function isPdf(file: File): boolean {
  return file.type === 'application/pdf' || extensionOf(file.name) === 'pdf';
}

/** Une image que le navigateur pourra peut-être convertir (JPG, PNG, mais aussi HEIC d'iPhone…). */
export function isImage(file: File): boolean {
  return file.type.startsWith('image/') || ['jpg', 'jpeg', 'png'].includes(extensionOf(file.name));
}

/** Le fichier final est-il accepté par le serveur (RG05) ? */
export function isAcceptedFile(file: File): boolean {
  return ACCEPTED_TYPES.includes(file.type) || ACCEPTED_EXTENSIONS.includes(extensionOf(file.name));
}

/**
 * Prépare une photo avant l'envoi :
 * - réduit sa taille (côté max 2000 px) : envoi plus rapide sur mobile, moins de dépassements des 5 Mo ;
 * - la ré-encode en JPEG, ce qui supprime les métadonnées EXIF (position GPS, modèle du téléphone).
 * Les PDF sont envoyés tels quels. En cas d'échec, le fichier d'origine est renvoyé.
 */
export async function prepareImage(file: File): Promise<File> {
  if (isPdf(file) || !isImage(file)) {
    return file;
  }
  try {
    const image = await loadImage(file);
    const scale = Math.min(1, MAX_IMAGE_SIDE / Math.max(image.naturalWidth, image.naturalHeight));
    const width = Math.round(image.naturalWidth * scale);
    const height = Math.round(image.naturalHeight * scale);

    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext('2d');
    if (!context || width === 0 || height === 0) {
      return file;
    }
    context.fillStyle = '#ffffff'; // fond blanc si l'image a de la transparence
    context.fillRect(0, 0, width, height);
    context.drawImage(image, 0, 0, width, height);

    const blob = await new Promise<Blob | null>(resolve => canvas.toBlob(resolve, 'image/jpeg', JPEG_QUALITY));
    if (!blob) {
      return file;
    }
    // Le serveur vérifie que l'extension correspond au contenu : on nomme donc le fichier .jpg
    return new File([blob], 'ordonnance.jpg', { type: 'image/jpeg', lastModified: Date.now() });
  } catch {
    return file;
  }
}

function loadImage(file: File): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () => {
      URL.revokeObjectURL(url);
      resolve(image);
    };
    image.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error('Image illisible'));
    };
    image.src = url;
  });
}
