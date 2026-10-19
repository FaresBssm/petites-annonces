import { Routes } from '@angular/router';
import { AccueilComponent } from './accueil/accueil.component';
import { ProfilComponent } from './profil/profil.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'annonces' },
  { path: 'annonces', component: AccueilComponent },
  { path: 'profil', component: ProfilComponent },
  { path: '**', redirectTo: 'annonces' },
];
