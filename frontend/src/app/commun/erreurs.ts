import { HttpErrorResponse } from '@angular/common/http';

/** Corps d'erreur ProblemDetail (RFC 9457) renvoyé par le backend. */
export interface ProblemDetail {
  status?: number;
  title?: string;
  detail?: string;
  errors?: Record<string, string>;
}

export function problem(erreur: unknown): ProblemDetail {
  return erreur instanceof HttpErrorResponse && erreur.error && typeof erreur.error === 'object'
    ? (erreur.error as ProblemDetail)
    : {};
}

/** Statuts traités globalement par l'interceptor : rien à afficher de plus dans l'écran. */
export function gereeGlobalement(erreur: unknown): boolean {
  return erreur instanceof HttpErrorResponse && [0, 401, 403, 429].includes(erreur.status);
}
