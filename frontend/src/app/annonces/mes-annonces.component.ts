import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AnnonceService } from '../api/annonce.service';
import { CategorieService } from '../api/categorie.service';
import { Annonce, LIBELLES_STATUT } from '../api/modeles';
import { NotificationService } from '../commun/notification.service';

@Component({
  selector: 'app-mes-annonces',
  imports: [RouterLink, CurrencyPipe, DatePipe],
  template: `
    <div class="barre">
      <h1 style="flex: 1; margin: 0">Mes annonces</h1>
      <a class="bouton" routerLink="/annonces/nouvelle">Publier une annonce</a>
    </div>

    @if (annonces(); as liste) {
      @for (a of liste; track a.id) {
        <div class="carte">
          <h3><a [routerLink]="['/annonces', a.id]">{{ a.titre }}</a></h3>
          <div class="meta">
            <span class="statut {{ a.statut }}">{{ libelles[a.statut] }}</span>
            · <span class="prix">{{ a.prix | currency: 'EUR' }}</span>
            · {{ categories.libelle(a.categorieId) }} · {{ a.dateModification | date: 'dd/MM/yyyy HH:mm' }}
          </div>
          @if (a.motifRejet) {
            <p class="erreur-champ">Motif du rejet : {{ a.motifRejet }} — modifiez l'annonce pour la soumettre à nouveau.</p>
          }
          <div class="actions">
            <a class="bouton secondaire" [routerLink]="['/annonces', a.id, 'modifier']">Modifier</a>
            @if (aConfirmer() === a.id) {
              <button class="bouton danger" type="button" (click)="supprimer(a.id)">Confirmer la suppression</button>
              <button class="bouton secondaire" type="button" (click)="aConfirmer.set(null)">Annuler</button>
            } @else {
              <button class="bouton secondaire" type="button" (click)="aConfirmer.set(a.id)">Supprimer</button>
            }
          </div>
        </div>
      } @empty {
        <p class="vide">Vous n'avez encore publié aucune annonce.</p>
      }
    } @else {
      <p class="vide">Chargement…</p>
    }
  `,
})
export class MesAnnoncesComponent implements OnInit {
  private readonly service = inject(AnnonceService);
  private readonly notifications = inject(NotificationService);
  protected readonly categories = inject(CategorieService);

  protected readonly libelles = LIBELLES_STATUT;
  protected readonly annonces = signal<Annonce[] | null>(null);
  protected readonly aConfirmer = signal<string | null>(null);

  ngOnInit(): void {
    this.categories.charger();
    this.service.mesAnnonces().subscribe({ next: liste => this.annonces.set(liste), error: () => undefined });
  }

  supprimer(id: string): void {
    this.service.supprimer(id).subscribe({
      next: () => {
        this.annonces.update(liste => (liste ?? []).filter(a => a.id !== id));
        this.notifications.succes('Annonce supprimée.');
      },
      error: () => this.aConfirmer.set(null),
    });
  }
}
