import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AnnonceService } from '../api/annonce.service';
import { CategorieService } from '../api/categorie.service';
import { AnnonceSaisie } from '../api/modeles';
import { problem } from '../commun/erreurs';
import { NotificationService } from '../commun/notification.service';

/**
 * Création (/annonces/nouvelle) et modification (/annonces/:id/modifier).
 * Les validations reprennent celles du backend pour guider la saisie ; le backend reste l'arbitre
 * et ses erreurs 400 (ProblemDetail.errors) sont affichées sous les champs concernés.
 */
@Component({
  selector: 'app-formulaire-annonce',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <h1>{{ id() ? 'Modifier l\\'annonce' : 'Publier une annonce' }}</h1>
    @if (id()) {
      <p class="meta">Après modification, l'annonce repasse en modération avant d'être à nouveau publiée.</p>
    }

    <form class="carte" [formGroup]="formulaire" (ngSubmit)="enregistrer()">
      <div class="champ">
        <label for="titre">Titre</label>
        <input id="titre" formControlName="titre" maxlength="100" />
        @if (erreur('titre'); as e) { <span class="erreur-champ">{{ e }}</span> }
      </div>
      <div class="champ">
        <label for="description">Description</label>
        <textarea id="description" formControlName="description" maxlength="2000"></textarea>
        @if (erreur('description'); as e) { <span class="erreur-champ">{{ e }}</span> }
      </div>
      <div class="champ">
        <label for="prix">Prix (€)</label>
        <input id="prix" type="number" min="0" step="0.01" formControlName="prix" />
        @if (erreur('prix'); as e) { <span class="erreur-champ">{{ e }}</span> }
      </div>
      <div class="champ">
        <label for="categorieId">Catégorie</label>
        <select id="categorieId" formControlName="categorieId">
          <option [ngValue]="null" disabled>Choisir…</option>
          @for (c of categories.categories(); track c.id) {
            <option [ngValue]="c.id">{{ c.libelle }}</option>
          }
        </select>
        @if (erreur('categorieId'); as e) { <span class="erreur-champ">{{ e }}</span> }
      </div>
      @if (erreurGenerale()) { <p class="erreur-champ">{{ erreurGenerale() }}</p> }
      <div class="actions">
        <button class="bouton" type="submit" [disabled]="envoi()">{{ id() ? 'Enregistrer' : 'Publier' }}</button>
        <a class="bouton secondaire" routerLink="/mes-annonces">Annuler</a>
      </div>
    </form>
  `,
})
export class FormulaireAnnonceComponent implements OnInit {
  /** Présent en modification seulement. */
  readonly id = input<string>();

  private readonly fb = inject(FormBuilder);
  private readonly annonces = inject(AnnonceService);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);
  protected readonly categories = inject(CategorieService);

  protected readonly formulaire = this.fb.group({
    titre: ['', [Validators.required, Validators.minLength(5), Validators.maxLength(100)]],
    description: ['', [Validators.maxLength(2000)]],
    prix: [null as number | null, [Validators.required, Validators.min(0)]],
    categorieId: [null as number | null, [Validators.required]],
  });

  protected readonly envoi = signal(false);
  protected readonly erreursServeur = signal<Record<string, string>>({});
  protected readonly erreurGenerale = signal('');
  private readonly soumis = signal(false);

  ngOnInit(): void {
    this.categories.charger();
    const id = this.id();
    if (id) {
      this.annonces.trouver(id).subscribe({
        next: a => this.formulaire.setValue({
          titre: a.titre, description: a.description ?? '', prix: a.prix, categorieId: a.categorieId,
        }),
        error: () => this.erreurGenerale.set('Impossible de charger l\'annonce.'),
      });
    }
  }

  protected erreur(champ: string): string | null {
    const serveur = this.erreursServeur()[champ];
    if (serveur) {
      return serveur;
    }
    const controle = this.formulaire.get(champ);
    if (!controle || !controle.errors || !(controle.touched || this.soumis())) {
      return null;
    }
    if (controle.errors['required']) return 'est obligatoire';
    if (controle.errors['minlength']) return `doit contenir au moins ${controle.errors['minlength'].requiredLength} caractères`;
    if (controle.errors['maxlength']) return `ne doit pas dépasser ${controle.errors['maxlength'].requiredLength} caractères`;
    if (controle.errors['min']) return 'doit être positif ou nul';
    return 'valeur invalide';
  }

  enregistrer(): void {
    this.soumis.set(true);
    this.erreursServeur.set({});
    this.erreurGenerale.set('');
    if (this.formulaire.invalid) {
      return;
    }
    const v = this.formulaire.getRawValue();
    const saisie: AnnonceSaisie = {
      titre: v.titre!.trim(), description: v.description || null, prix: v.prix!, categorieId: v.categorieId!,
    };
    const id = this.id();
    this.envoi.set(true);
    (id ? this.annonces.modifier(id, saisie) : this.annonces.creer(saisie)).subscribe({
      next: () => {
        this.notifications.succes(id ? 'Annonce modifiée : elle repasse en modération.' : 'Annonce créée : elle sera visible après modération.');
        this.router.navigateByUrl('/mes-annonces');
      },
      error: (e: HttpErrorResponse) => {
        this.envoi.set(false);
        const p = problem(e);
        if (e.status === 400 && p.errors) {
          this.erreursServeur.set(p.errors);
        } else if ([404, 409].includes(e.status)) {
          this.erreurGenerale.set(p.detail ?? 'Opération impossible.');
        }
      },
    });
  }
}
