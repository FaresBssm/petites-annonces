import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { config } from '../config';
import { Annonce, AnnonceSaisie, Page } from './modeles';

@Injectable({ providedIn: 'root' })
export class AnnonceService {
  private readonly http = inject(HttpClient);
  private readonly url = `${config.apiUrl}/annonces`;
  private readonly urlModeration = `${config.apiUrl}/moderation/annonces`;

  listerPubliees(page: number, size: number, categorieId: number | null): Observable<Page<Annonce>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (categorieId !== null) {
      params = params.set('categorieId', categorieId);
    }
    return this.http.get<Page<Annonce>>(this.url, { params });
  }

  trouver(id: string): Observable<Annonce> {
    return this.http.get<Annonce>(`${this.url}/${id}`);
  }

  mesAnnonces(): Observable<Annonce[]> {
    return this.http.get<Annonce[]>(`${this.url}/mes-annonces`);
  }

  creer(saisie: AnnonceSaisie): Observable<Annonce> {
    return this.http.post<Annonce>(this.url, saisie);
  }

  modifier(id: string, saisie: AnnonceSaisie): Observable<Annonce> {
    return this.http.put<Annonce>(`${this.url}/${id}`, saisie);
  }

  supprimer(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  enAttente(): Observable<Annonce[]> {
    return this.http.get<Annonce[]>(this.urlModeration);
  }

  publier(id: string): Observable<Annonce> {
    return this.http.patch<Annonce>(`${this.urlModeration}/${id}/publier`, null);
  }

  rejeter(id: string, motif: string): Observable<Annonce> {
    return this.http.patch<Annonce>(`${this.urlModeration}/${id}/rejeter`, { motif });
  }
}
