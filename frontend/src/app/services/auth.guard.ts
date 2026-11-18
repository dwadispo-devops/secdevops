import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { ProfileService, Profile } from './profile.service';

function clearSession(): void {
  for (const key of ['access_token', 'refresh_token', 'last_prescription_id']) {
    sessionStorage.removeItem(key);
  }
}

function checkAccess(
  stateUrl: string,
  allowedRoles?: Profile['role'][]
) {
  const router = inject(Router);
  const profileService = inject(ProfileService);
  const token = sessionStorage.getItem('access_token');

  if (!token) {
    return of(router.createUrlTree(['/connexion'], {
      queryParams: { retour: stateUrl },
    }));
  }

  return profileService.get().pipe(
    map(profile => {
      if (!allowedRoles || allowedRoles.includes(profile.role)) {
        return true;
      }

      if (profile.role === 'PHARMACIEN') {
        return router.createUrlTree(['/pharmacien/demandes']);
      }

      if (profile.role === 'PATIENT') {
        return router.createUrlTree(['/scan']);
      }

      return router.createUrlTree(['/']);
    }),
    catchError(() => {
      clearSession();
      return of(router.createUrlTree(['/connexion'], {
        queryParams: { retour: stateUrl },
      }));
    })
  );
}

export const authGuard: CanActivateFn = (_route, state) =>
  checkAccess(state.url);

export function roleGuard(...roles: Profile['role'][]): CanActivateFn {
  return (_route, state) => checkAccess(state.url, roles);
}
