import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';
import { chargerConfig } from './app/config';

// Prix et dates au format français (350,00 €, 19/10/2026)
registerLocaleData(localeFr);

// Adresses de Keycloak et de la gateway propres à l'environnement, lues AVANT le démarrage
chargerConfig()
  .then(() => bootstrapApplication(AppComponent, appConfig))
  .catch((err) => console.error(err));
