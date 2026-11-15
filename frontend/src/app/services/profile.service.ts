import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/** US20 : profil de l'utilisateur connecté (auth-service). */
export interface Profile {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: 'PATIENT' | 'PHARMACIEN' | 'ADMIN';
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/auth/me';

  private headers() {
    return { Authorization: `Bearer ${sessionStorage.getItem('access_token') ?? ''}` };
  }

  get(): Observable<Profile> {
    return this.http.get<Profile>(this.url, { headers: this.headers() });
  }

  update(firstName: string, lastName: string): Observable<Profile> {
    return this.http.put<Profile>(this.url, { firstName, lastName }, { headers: this.headers() });
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.put<void>(`${this.url}/password`, { currentPassword, newPassword }, { headers: this.headers() });
  }

  /** US20 / loi 09-08 : suppression définitive du compte et de ses données. */
  deleteAccount(password: string): Observable<void> {
    return this.http.post<void>(`${this.url}/suppression`, { password }, { headers: this.headers() });
  }
}
