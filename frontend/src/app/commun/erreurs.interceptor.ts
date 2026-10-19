import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
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

  return suivant(requete).pipe(
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
        case 429: {
          // FR-05 : délai lu dans Retry-After (exposé par le plugin cors de Kong)
          const secondes = Number(erreur.headers.get('Retry-After')) || 60;
          notifications.limiteAtteinte(secondes);
          break;
        }
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
