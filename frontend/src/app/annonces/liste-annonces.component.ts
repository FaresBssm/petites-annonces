import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AnnonceService } from '../api/annonce.service';
import { CategorieService } from '../api/categorie.service';
import { Annonce, Page } from '../api/modeles';

@Component({
  selector: 'app-liste-annonces',
  imports: [RouterLink, FormsModule, CurrencyPipe, DatePipe],
  template: `
    <h1>Annonces publiées</h1>

    <div class="barre">
      <div class="champ">
        <label for="categorie">Catégorie</label>
        <select id="categorie" [ngModel]="categorieId()" (ngModelChange)="filtrer($event)">
          <option [ngValue]="null">Toutes</option>
          @for (c of categories.categories(); track c.id) {
            <option [ngValue]="c.id">{{ c.libelle }}</option>
          }
        </select>
      </div>
      <a class="bouton" routerLink="/annonces/nouvelle">Publier une annonce</a>
    </div>

    @if (page(); as p) {
      @for (a of p.contenu; track a.id) {
        <a class="carte lien" [routerLink]="['/annonces', a.id]">
          <h3>{{ a.titre }}</h3>
          <div class="meta">
            <span class="prix">{{ a.prix | currency: 'EUR' }}</span> ·
            {{ categories.libelle(a.categorieId) }} · {{ a.dateCreation | date: 'dd/MM/yyyy HH:mm' }}
          </div>
        </a>
      } @empty {
        <p class="vide">Aucune annonce publiée{{ categorieId() !== null ? ' dans cette catégorie' : '' }}.</p>
      }

      @if (p.totalPages > 1) {
        <div class="pagination">
          <button class="bouton secondaire" type="button" [disabled]="p.page === 0" (click)="charger(p.page - 1)">Précédente</button>
          <span class="meta">Page {{ p.page + 1 }} / {{ p.totalPages }} ({{ p.totalElements }} annonces)</span>
          <button class="bouton secondaire" type="button" [disabled]="p.page + 1 >= p.totalPages" (click)="charger(p.page + 1)">Suivante</button>
        </div>
      }
    } @else {
      <p class="vide">Chargement…</p>
    }
  `,
  styles: [`.lien { display: block; color: inherit; text-decoration: none; } .lien:hover { border-color: var(--accent); }`],
})
export class ListeAnnoncesComponent implements OnInit {
  private readonly annonces = inject(AnnonceService);
  protected readonly categories = inject(CategorieService);

  protected readonly page = signal<Page<Annonce> | null>(null);
  protected readonly categorieId = signal<number | null>(null);
  private readonly taille = 10;

  ngOnInit(): void {
    this.categories.charger();
    this.charger(0);
  }

  filtrer(categorieId: number | null): void {
    this.categorieId.set(categorieId);
    this.charger(0);
  }

  charger(numero: number): void {
    this.annonces.listerPubliees(numero, this.taille, this.categorieId()).subscribe({
      next: p => this.page.set(p),
      error: () => undefined,
    });
  }
}
