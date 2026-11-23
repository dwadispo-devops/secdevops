import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { Profile } from './profile.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  readonly profile = signal<Profile | null>(null);
  readonly connected = signal(false);

  loadProfile(): Observable<Profile> {
    return this.http.get<Profile>('/api/auth/me', {
      headers: {
        Authorization: `Bearer ${sessionStorage.getItem('access_token') ?? ''}`,
      },
    }).pipe(
      tap(profile => {
        this.profile.set(profile);
        this.connected.set(true);
      }),
    );
  }

  clearSession(): void {
    for (const key of ['access_token', 'refresh_token', 'last_prescription_id']) {
      sessionStorage.removeItem(key);
    }

    this.profile.set(null);
    this.connected.set(false);
  }

  setProfile(profile: Profile): void {
    this.profile.set(profile);
    this.connected.set(true);
  }
}
