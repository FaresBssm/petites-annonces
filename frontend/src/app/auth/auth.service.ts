import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';
import { filter } from 'rxjs';
import { authConfig } from './auth.config';

export type Role = 'USER' | 'MODERATEUR' | 'ADMIN';

interface ClaimsAccessToken {
  sub: string;
  preferred_username?: string;
  realm_access?: { roles?: string[] };
}

/**
 * Point d'entrée unique de l'authentification (flux Authorization Code + PKCE).
 * Les rôles servent seulement à adapter l'affichage : la sécurité réelle est appliquée
 * par la gateway et le backend.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oauth = inject(OAuthService);
  private readonly router = inject(Router);

  private readonly jeton = signal<string | null>(null);

  private readonly claims = computed(() => decoder(this.jeton()));
  readonly connecte = computed(() => this.jeton() !== null);
  /** Claim sub : même valeur que X_USER_ID côté backend, donc que auteurId des annonces. */
  readonly userId = computed(() => this.claims()?.sub ?? '');
  readonly nomUtilisateur = computed(() => this.claims()?.preferred_username ?? '');
  readonly roles = computed<Role[]>(() =>
    (this.claims()?.realm_access?.roles ?? []).filter((r): r is Role => ['USER', 'MODERATEUR', 'ADMIN'].includes(r)),
  );

  /** Exécuté avant le démarrage de l'application (provideAppInitializer). */
  async initialiser(): Promise<void> {
    this.oauth.configure(authConfig);
    this.oauth.events.subscribe(() => this.synchroniser());
    // Refresh token expiré ou session Keycloak fermée : retour au login
    this.oauth.events
      .pipe(filter(e => e.type === 'token_refresh_error'))
      .subscribe(() => this.seConnecter());

    await this.oauth.loadDiscoveryDocument();
    // Retour de Keycloak avec ?code=... : échange du code + code_verifier sur /token (KC-03)
    await this.oauth.tryLoginCodeFlow();
    // La librairie retire code et state de l'URL ; on retire aussi iss (RFC 9207, ajouté par Keycloak)
    const url = new URL(window.location.href);
    if (url.searchParams.has('iss')) {
      url.searchParams.delete('iss');
      history.replaceState(history.state, '', url.pathname + url.search + url.hash);
    }

    // Token absent ou expiré mais refresh token encore en sessionStorage (rechargement de page)
    if (!this.oauth.hasValidAccessToken() && this.oauth.getRefreshToken()) {
      try {
        await this.oauth.refreshToken();
      } catch {
        // refresh impossible : login complet ci-dessous
      }
    }

    if (!this.oauth.hasValidAccessToken()) {
      this.seConnecter();
      // L'application ne démarre pas : le navigateur part vers Keycloak
      return new Promise<void>(() => undefined);
    }

    // Renouvellement automatique avant expiration via le refresh token (FR-04)
    this.oauth.setupAutomaticSilentRefresh();
    this.synchroniser();

    // Retour sur la page demandée avant le login (mémorisée dans le paramètre state)
    const cible = this.oauth.state ? decodeURIComponent(this.oauth.state) : '';
    if (cible.startsWith('/') && cible !== '/') {
      queueMicrotask(() => this.router.navigateByUrl(cible));
    }
  }

  /** Redirection vers Keycloak : response_type=code, code_challenge, code_challenge_method=S256 (KC-02). */
  seConnecter(): void {
    const ici = window.location.pathname + window.location.search;
    this.oauth.initCodeFlow(encodeURIComponent(ici));
  }

  /** Ferme la session Keycloak via l'endpoint end_session (KC-06), puis revient sur l'application. */
  seDeconnecter(): void {
    this.oauth.logOut();
  }

  aLeRole(role: Role): boolean {
    return this.roles().includes(role);
  }

  jetonAcces(): string | null {
    return this.oauth.hasValidAccessToken() ? this.oauth.getAccessToken() : null;
  }

  private synchroniser(): void {
    this.jeton.set(this.oauth.hasValidAccessToken() ? this.oauth.getAccessToken() : null);
  }
}

function decoder(jwt: string | null): ClaimsAccessToken | null {
  if (!jwt) {
    return null;
  }
  try {
    const base64 = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map(c => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    );
    return JSON.parse(json) as ClaimsAccessToken;
  } catch {
    return null;
  }
}
