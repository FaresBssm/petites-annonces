import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService, Role } from './auth.service';

/**
 * Masque une route aux utilisateurs sans le rôle (redirection vers la liste, FR-03).
 * Confort d'affichage uniquement : la sécurité réelle est le @PreAuthorize du backend.
 */
export function roleGuard(role: Role): CanActivateFn {
  return () => inject(AuthService).aLeRole(role) || inject(Router).createUrlTree(['/annonces']);
}
