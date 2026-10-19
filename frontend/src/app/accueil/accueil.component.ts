import { Component, inject } from '@angular/core';
import { AuthService } from '../auth/auth.service';

/** Page provisoire (étape 17) : prouve que le login PKCE a abouti. Remplacée par la liste des annonces. */
@Component({
  selector: 'app-accueil',
  template: `
    <h1>Bienvenue {{ auth.nomUtilisateur() }}</h1>
    <p>Connecté via Keycloak (Authorization Code + PKCE). Rôles du token : {{ auth.roles().join(', ') }}</p>
  `,
})
export class AccueilComponent {
  protected readonly auth = inject(AuthService);
}
