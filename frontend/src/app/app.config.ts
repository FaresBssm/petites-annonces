import { ApplicationConfig, inject, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { OAuthStorage, provideOAuthClient } from 'angular-oauth2-oidc';

import { routes } from './app.routes';
import { AuthService } from './auth/auth.service';
import { jetonInterceptor } from './auth/jeton.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([jetonInterceptor])),
    // Notre propre interceptor ajoute le Bearer : celui de la librairie reste désactivé
    provideOAuthClient({ resourceServer: { sendAccessToken: false } }),
    // Tokens en sessionStorage, jamais en localStorage (FR-02)
    { provide: OAuthStorage, useFactory: () => sessionStorage },
    provideAppInitializer(() => inject(AuthService).initialiser()),
  ],
};
