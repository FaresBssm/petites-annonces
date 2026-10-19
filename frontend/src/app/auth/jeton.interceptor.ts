import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { config } from '../config';
import { AuthService } from './auth.service';

/**
 * Ajoute Authorization: Bearer <access_token> aux SEULS appels vers la gateway (FR-01).
 * Les appels de la librairie OIDC vers Keycloak (/token, discovery...) ne le reçoivent pas.
 */
export const jetonInterceptor: HttpInterceptorFn = (requete, suivant) => {
  if (!requete.url.startsWith(config.apiUrl)) {
    return suivant(requete);
  }
  const jeton = inject(AuthService).jetonAcces();
  return suivant(jeton ? requete.clone({ setHeaders: { Authorization: `Bearer ${jeton}` } }) : requete);
};
