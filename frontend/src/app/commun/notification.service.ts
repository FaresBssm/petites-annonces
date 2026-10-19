import { Injectable, computed, signal } from '@angular/core';

export type TypeNotification = 'erreur' | 'attention' | 'succes';

interface Notification {
  type: TypeNotification;
  message: string;
}

/** Bandeau de messages global ; gère le compte à rebours du rate limiting (FR-05). */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly courante = signal<Notification | null>(null);
  private readonly finLimite = signal<number | null>(null);
  private readonly maintenant = signal(Date.now());
  private minuteur?: ReturnType<typeof setInterval>;

  readonly secondesRestantes = computed(() => {
    const fin = this.finLimite();
    return fin === null ? 0 : Math.max(0, Math.ceil((fin - this.maintenant()) / 1000));
  });

  readonly notification = computed<Notification | null>(() => {
    if (this.finLimite() !== null && this.secondesRestantes() > 0) {
      return {
        type: 'attention',
        message: `Limite de 3 requêtes par minute atteinte : réessayez dans ${this.secondesRestantes()} s.`,
      };
    }
    return this.courante();
  });

  erreur(message: string): void {
    this.afficher({ type: 'erreur', message });
  }

  succes(message: string): void {
    this.afficher({ type: 'succes', message });
  }

  /** 429 : délai lu dans l'en-tête Retry-After. */
  limiteAtteinte(secondes: number): void {
    this.courante.set(null);
    this.finLimite.set(Date.now() + secondes * 1000);
    this.maintenant.set(Date.now());
    clearInterval(this.minuteur);
    this.minuteur = setInterval(() => {
      this.maintenant.set(Date.now());
      if (this.secondesRestantes() === 0) {
        clearInterval(this.minuteur);
        this.finLimite.set(null);
      }
    }, 1000);
  }

  fermer(): void {
    this.courante.set(null);
    this.finLimite.set(null);
    clearInterval(this.minuteur);
  }

  private afficher(notification: Notification): void {
    if (this.secondesRestantes() > 0) {
      return; // le compte à rebours du 429 reste prioritaire
    }
    this.courante.set(notification);
  }
}
