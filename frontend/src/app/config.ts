/**
 * Adresses vues par le NAVIGATEUR (la SPA s'exécute chez l'utilisateur, pas dans Docker) :
 * Keycloak sur 8180, API uniquement via la gateway Kong sur 8080.
 */
export const config = {
  /** Doit être identique au claim iss des tokens (KC_HOSTNAME côté Keycloak). */
  issuer: 'http://localhost:8180/realms/annonces',
  clientId: 'annonces-front',
  /** Seuls les appels vers cette base reçoivent le header Authorization. */
  apiUrl: 'http://localhost:8080/api',
} as const;
