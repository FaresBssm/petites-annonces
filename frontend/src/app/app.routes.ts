import { Routes } from '@angular/router';
import { AccueilComponent } from './accueil/accueil.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'annonces' },
  { path: 'annonces', component: AccueilComponent },
  { path: '**', redirectTo: 'annonces' },
];
