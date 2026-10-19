import { AuthConfig } from 'angular-oauth2-oidc';
import { config } from '../config';

/** Client public annonces-front : Authorization Code + PKCE (S256), sans secret. */
export const authConfig: AuthConfig = {
  issuer: config.issuer,
  clientId: config.clientId,
  responseType: 'code',
  scope: 'openid profile email',
  redirectUri: window.location.origin + '/',
  postLogoutRedirectUri: window.location.origin + '/',

  // PKCE activé (code_challenge_method=S256) : c'est la valeur par défaut, explicitée ici
  disablePKCE: false,

  // Renouvellement par refresh token (pas d'iframe de "silent refresh"), à 75 % de la durée de vie
  useSilentRefresh: false,
  timeoutFactor: 0.75,

  // http://localhost est autorisé ; toute autre adresse devrait être en HTTPS
  requireHttps: 'remoteOnly',
  clearHashAfterLogin: true,
  sessionChecksEnabled: false,
  showDebugInformation: false,
};
