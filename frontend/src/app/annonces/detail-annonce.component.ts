import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AnnonceService } from '../api/annonce.service';
import { CategorieService } from '../api/categorie.service';
import { Annonce, LIBELLES_STATUT } from '../api/modeles';
import { AuthService } from '../auth/auth.service';
import { NotificationService } from '../commun/notification.service';

@Component({
  selector: 'app-detail-annonce',
  imports: [RouterLink, CurrencyPipe, DatePipe],
  template: `
    @if (annonce(); as a) {
      <h1>{{ a.titre }}</h1>
      <div class="carte">
        <p><span class="statut {{ a.statut }}">{{ libelles[a.statut] }}</span></p>
        <p class="prix">{{ a.prix | currency: 'EUR' }}</p>
        <p class="meta">{{ categories.libelle(a.categorieId) }} · publiée le {{ a.dateCreation | date: 'dd/MM/yyyy HH:mm' }}
          · modifiée le {{ a.dateModification | date: 'dd/MM/yyyy HH:mm' }}</p>
        <p style="white-space: pre-line">{{ a.description || 'Pas de description.' }}</p>
        @if (a.motifRejet) {
          <p class="erreur-champ">Motif du rejet : {{ a.motifRejet }}</p>
        }

        <!-- Confort d'affichage : le backend refuse de toute façon (403) si l'action n'est pas permise -->
        <div class="actions">
          @if (estAuteur()) {
            <a class="bouton secondaire" [routerLink]="['/annonces', a.id, 'modifier']">Modifier</a>
          }
          @if (estAuteur() || auth.aLeRole('MODERATEUR')) {
            @if (confirmation()) {
              <button class="bouton danger" type="button" (click)="supprimer(a.id)">Confirmer la suppression</button>
              <button class="bouton secondaire" type="button" (click)="confirmation.set(false)">Annuler</button>
            } @else {
              <button class="bouton secondaire" type="button" (click)="confirmation.set(true)">Supprimer</button>
            }
          }
        </div>
      </div>
    } @else if (introuvable()) {
      <h1>Annonce introuvable</h1>
      <p class="vide">Cette annonce n'existe pas ou n'est pas visible.</p>
    } @else {
      <p class="vide">Chargement…</p>
    }
    <p><a routerLink="/annonces">← Retour aux annonces</a></p>
  `,
})
export class DetailAnnonceComponent implements OnInit {
  /** Paramètre de route :id (withComponentInputBinding). */
  readonly id = input.required<string>();

  protected readonly auth = inject(AuthService);
  protected readonly categories = inject(CategorieService);
  private readonly annonces = inject(AnnonceService);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);

  protected readonly libelles = LIBELLES_STATUT;
  protected readonly annonce = signal<Annonce | null>(null);
  protected readonly introuvable = signal(false);
  protected readonly confirmation = signal(false);
  protected readonly estAuteur = computed(() => this.annonce()?.auteurId === this.auth.userId());

  ngOnInit(): void {
    this.categories.charger();
    this.annonces.trouver(this.id()).subscribe({
      next: a => this.annonce.set(a),
      error: (e: HttpErrorResponse) => this.introuvable.set(e.status === 404 || e.status === 400),
    });
  }

  supprimer(id: string): void {
    this.annonces.supprimer(id).subscribe({
      next: () => {
        this.notifications.succes('Annonce supprimée.');
        this.router.navigateByUrl('/annonces');
      },
      error: () => this.confirmation.set(false),
    });
  }
}
