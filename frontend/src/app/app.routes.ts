import { Routes } from '@angular/router';
import { DetailAnnonceComponent } from './annonces/detail-annonce.component';
import { FormulaireAnnonceComponent } from './annonces/formulaire-annonce.component';
import { ListeAnnoncesComponent } from './annonces/liste-annonces.component';
import { MesAnnoncesComponent } from './annonces/mes-annonces.component';
import { ProfilComponent } from './profil/profil.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'annonces' },
  { path: 'annonces', component: ListeAnnoncesComponent, title: 'Annonces' },
  { path: 'annonces/nouvelle', component: FormulaireAnnonceComponent, title: 'Publier une annonce' },
  { path: 'annonces/:id/modifier', component: FormulaireAnnonceComponent, title: 'Modifier l\'annonce' },
  { path: 'annonces/:id', component: DetailAnnonceComponent, title: 'Annonce' },
  { path: 'mes-annonces', component: MesAnnoncesComponent, title: 'Mes annonces' },
  { path: 'profil', component: ProfilComponent, title: 'Mon profil' },
  { path: '**', redirectTo: 'annonces' },
];
