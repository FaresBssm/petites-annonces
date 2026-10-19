import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AnnonceService } from '../api/annonce.service';
import { CategorieService } from '../api/categorie.service';
import { Annonce } from '../api/modeles';
import { problem } from '../commun/erreurs';
import { NotificationService } from '../commun/notification.service';

@Component({
  selector: 'app-moderation',
  imports: [FormsModule, RouterLink, CurrencyPipe, DatePipe],
  template: `
    <h1>Modération</h1>
    <p class="meta">Annonces en attente, les plus anciennes d'abord.</p>

    @if (file(); as liste) {
      @for (a of liste; track a.id) {
        <div class="carte">
          <h3><a [routerLink]="['/annonces', a.id]">{{ a.titre }}</a></h3>
          <div class="meta">
            <span class="prix">{{ a.prix | currency: 'EUR' }}</span> · {{ categories.libelle(a.categorieId) }}
            · en attente depuis le {{ a.dateModification | date: 'dd/MM/yyyy HH:mm' }}
          </div>
          <p style="white-space: pre-line">{{ a.description || 'Pas de description.' }}</p>

          @if (rejetEnCours() === a.id) {
            <div class="champ">
              <label [for]="'motif-' + a.id">Motif du rejet (obligatoire)</label>
              <input [id]="'motif-' + a.id" [(ngModel)]="motif" maxlength="500" />
              @if (erreurMotif()) { <span class="erreur-champ">{{ erreurMotif() }}</span> }
            </div>
            <div class="actions">
              <button class="bouton danger" type="button" (click)="rejeter(a.id)">Confirmer le rejet</button>
              <button class="bouton secondaire" type="button" (click)="rejetEnCours.set(null)">Annuler</button>
            </div>
          } @else {
            <div class="actions">
              <button class="bouton" type="button" (click)="publier(a.id)">Publier</button>
              <button class="bouton secondaire" type="button" (click)="ouvrirRejet(a.id)">Rejeter…</button>
            </div>
          }
        </div>
      } @empty {
        <p class="vide">Aucune annonce en attente.</p>
      }
    } @else {
      <p class="vide">Chargement…</p>
    }
  `,
})
export class ModerationComponent implements OnInit {
  private readonly annonces = inject(AnnonceService);
  private readonly notifications = inject(NotificationService);
  protected readonly categories = inject(CategorieService);

  protected readonly file = signal<Annonce[] | null>(null);
  protected readonly rejetEnCours = signal<string | null>(null);
  protected readonly erreurMotif = signal('');
  protected motif = '';

  ngOnInit(): void {
    this.categories.charger();
    this.annonces.enAttente().subscribe({ next: liste => this.file.set(liste), error: () => undefined });
  }

  publier(id: string): void {
    this.annonces.publier(id).subscribe({
      next: () => {
        this.retirer(id);
        this.notifications.succes('Annonce publiée.');
      },
      error: (e: HttpErrorResponse) => this.signalerConflit(e),
    });
  }

  ouvrirRejet(id: string): void {
    this.motif = '';
    this.erreurMotif.set('');
    this.rejetEnCours.set(id);
  }

  rejeter(id: string): void {
    // Le backend exige le motif (400 sinon, MT-07) : on guide la saisie avant l'envoi
    if (!this.motif.trim()) {
      this.erreurMotif.set('Le motif est obligatoire.');
      return;
    }
    this.annonces.rejeter(id, this.motif.trim()).subscribe({
      next: () => {
        this.retirer(id);
        this.rejetEnCours.set(null);
        this.notifications.succes('Annonce rejetée.');
      },
      error: (e: HttpErrorResponse) => {
        const p = problem(e);
        if (e.status === 400) {
          this.erreurMotif.set(p.errors?.['motif'] ?? p.detail ?? 'Motif invalide.');
        } else {
          this.signalerConflit(e);
        }
      },
    });
  }

  private retirer(id: string): void {
    this.file.update(liste => (liste ?? []).filter(a => a.id !== id));
  }

  private signalerConflit(e: HttpErrorResponse): void {
    if (e.status === 404 || e.status === 409) {
      this.notifications.erreur(problem(e).detail ?? 'Annonce déjà traitée.');
      this.retirer(e.url?.split('/').at(-2) ?? '');
    }
  }
}
