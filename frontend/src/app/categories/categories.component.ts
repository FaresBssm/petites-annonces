import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CategorieService } from '../api/categorie.service';
import { problem } from '../commun/erreurs';
import { NotificationService } from '../commun/notification.service';

@Component({
  selector: 'app-categories',
  imports: [FormsModule],
  template: `
    <h1>Catégories</h1>

    <form class="carte barre" (ngSubmit)="creer()">
      <div class="champ" style="flex: 1; margin: 0">
        <label for="libelle">Nouvelle catégorie</label>
        <input id="libelle" name="libelle" [(ngModel)]="libelle" maxlength="50" placeholder="ex. Animaux" />
        @if (erreur()) { <span class="erreur-champ">{{ erreur() }}</span> }
      </div>
      <button class="bouton" type="submit">Créer</button>
    </form>

    @for (c of categories.categories(); track c.id) {
      <div class="carte barre" style="margin-bottom: .5rem">
        <span style="flex: 1">{{ c.libelle }}</span>
        @if (aConfirmer() === c.id) {
          <button class="bouton danger" type="button" (click)="supprimer(c.id)">Confirmer la suppression</button>
          <button class="bouton secondaire" type="button" (click)="aConfirmer.set(null)">Annuler</button>
        } @else {
          <button class="bouton secondaire" type="button" (click)="aConfirmer.set(c.id)">Supprimer</button>
        }
      </div>
    } @empty {
      <p class="vide">Aucune catégorie.</p>
    }
  `,
})
export class CategoriesComponent implements OnInit {
  protected readonly categories = inject(CategorieService);
  private readonly notifications = inject(NotificationService);

  protected libelle = '';
  protected readonly erreur = signal('');
  protected readonly aConfirmer = signal<number | null>(null);

  ngOnInit(): void {
    this.categories.charger();
  }

  creer(): void {
    const libelle = this.libelle.trim();
    if (libelle.length < 2 || libelle.length > 50) {
      this.erreur.set('Le libellé doit contenir entre 2 et 50 caractères.');
      return;
    }
    this.erreur.set('');
    this.categories.creer(libelle).subscribe({
      next: c => {
        this.libelle = '';
        this.notifications.succes(`Catégorie « ${c.libelle} » créée.`);
      },
      error: (e: HttpErrorResponse) => {
        const p = problem(e);
        if (e.status === 400 || e.status === 409) {
          this.erreur.set(p.errors?.['libelle'] ?? p.detail ?? 'Libellé refusé.');
        }
      },
    });
  }

  supprimer(id: number): void {
    this.categories.supprimer(id).subscribe({
      next: () => this.notifications.succes('Catégorie supprimée.'),
      error: (e: HttpErrorResponse) => {
        this.aConfirmer.set(null);
        // 409 : catégorie utilisée par au moins une annonce (MT-09)
        if (e.status === 409 || e.status === 404) {
          this.notifications.erreur(problem(e).detail ?? 'Suppression impossible.');
        }
      },
    });
  }
}
