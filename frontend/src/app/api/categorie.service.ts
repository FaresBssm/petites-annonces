import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { config } from '../config';
import { Categorie } from './modeles';

/**
 * Catégories chargées une fois puis gardées en mémoire : avec 3 requêtes / minute,
 * chaque appel évité compte. Le cache est rafraîchi après création ou suppression.
 */
@Injectable({ providedIn: 'root' })
export class CategorieService {
  private readonly http = inject(HttpClient);
  private readonly url = `${config.apiUrl}/categories`;
  private chargement = false;

  readonly categories = signal<Categorie[]>([]);

  /** Charge les catégories si ce n'est pas déjà fait. */
  charger(forcer = false): void {
    if (this.chargement || (!forcer && this.categories().length > 0)) {
      return;
    }
    this.chargement = true;
    this.http.get<Categorie[]>(this.url).subscribe({
      next: liste => {
        this.categories.set(liste);
        this.chargement = false;
      },
      error: () => (this.chargement = false),
    });
  }

  libelle(id: number): string {
    return this.categories().find(c => c.id === id)?.libelle ?? `Catégorie ${id}`;
  }

  creer(libelle: string): Observable<Categorie> {
    return this.http.post<Categorie>(this.url, { libelle }).pipe(
      tap(c => this.categories.update(liste => [...liste, c].sort((a, b) => a.libelle.localeCompare(b.libelle)))),
    );
  }

  supprimer(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`).pipe(
      tap(() => this.categories.update(liste => liste.filter(c => c.id !== id))),
    );
  }
}
