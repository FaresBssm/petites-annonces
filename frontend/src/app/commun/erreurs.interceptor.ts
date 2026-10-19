import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, retry, throwError, timer } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { config } from '../config';
import { NotificationService } from './notification.service';

/**
 * Réactions globales aux erreurs de la gateway. Les erreurs 400 / 404 / 409, propres à un
 * écran (formulaire, ressource), sont laissées aux composants : l'erreur est toujours relancée.
 */
export const erreursInterceptor: HttpInterceptorFn = (requete, suivant) => {
  if (!requete.url.startsWith(config.apiUrl)) {
    return suivant(requete);
  }
  const auth = inject(AuthService);
  const notifications = inject(NotificationService);
  const delaiRetryAfter = (erreur: HttpErrorResponse) => Number(erreur.headers.get('Retry-After')) || 60;

  return suivant(requete).pipe(
    // 429 sur une LECTURE : compte à rebours puis nouvel essai automatique, l'écran se remplit seul.
    // Les écritures ne sont jamais rejouées : l'utilisateur garde la main.
    retry({
      count: 1,
      delay: (erreur: HttpErrorResponse) => {
        if (requete.method === 'GET' && erreur.status === 429) {
          const secondes = delaiRetryAfter(erreur);
          notifications.limiteAtteinte(secondes);
          return timer((secondes + 1) * 1000);
        }
        return throwError(() => erreur);
      },
    }),
    catchError((erreur: HttpErrorResponse) => {
      switch (erreur.status) {
        case 401:
          // Token absent, expiré ou refusé par la gateway : retour au login
          auth.seConnecter();
          break;
        case 403:
          // FR-06 : message, sans déconnexion
          notifications.erreur('Accès refusé : vous n\'avez pas les droits nécessaires pour cette action.');
          break;
        case 429:
          // FR-05 : délai lu dans Retry-After (exposé par le plugin cors de Kong)
          notifications.limiteAtteinte(delaiRetryAfter(erreur));
          break;
        case 0:
          notifications.erreur('API injoignable : la gateway ne répond pas.');
          break;
        default:
          if (erreur.status >= 500) {
            notifications.erreur('Erreur du serveur, réessayez plus tard.');
          }
      }
      return throwError(() => erreur);
    }),
  );
};
