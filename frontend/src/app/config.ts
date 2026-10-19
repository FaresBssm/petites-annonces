/**
 * Adresses vues par le NAVIGATEUR (la SPA s'exécute chez l'utilisateur, pas dans Docker).
 * Valeurs par défaut = développement local ; en déploiement, elles sont remplacées au démarrage
 * par /config.json, généré par le conteneur Nginx à partir de ses variables d'environnement
 * (même image pour tous les environnements).
 */
export const config = {
  /** Doit être identique au claim iss des tokens (KC_HOSTNAME côté Keycloak). */
  issuer: 'http://localhost:8180/realms/annonces',
  clientId: 'annonces-front',
  /** Seuls les appels vers cette base reçoivent le header Authorization. */
  apiUrl: 'http://localhost:8080/api',
};

/** Charge /config.json s'il existe (absent avec ng serve : on garde les valeurs locales). */
export async function chargerConfig(): Promise<void> {
  try {
    const reponse = await fetch('/config.json', { cache: 'no-store' });
    if (reponse.ok) {
      const valeurs = (await reponse.json()) as Partial<typeof config>;
      if (valeurs.issuer) config.issuer = valeurs.issuer;
      if (valeurs.apiUrl) config.apiUrl = valeurs.apiUrl;
    }
  } catch {
    // pas de config.json : valeurs locales
  }
}
