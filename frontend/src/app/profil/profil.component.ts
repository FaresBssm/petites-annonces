import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { config } from '../config';

interface Moi {
  userId: string;
  roles: string[];
}

/** Ce que le BACKEND voit de l'utilisateur : identité reconstruite depuis X_USER_ID / X_ROLE_ID. */
@Component({
  selector: 'app-profil',
  template: `
    <h1>Mon profil</h1>
    <div class="carte">
      <h3>Vu par le navigateur (access token)</h3>
      <p class="meta">Utilisateur : <strong>{{ auth.nomUtilisateur() }}</strong> — rôles : {{ auth.roles().join(', ') }}</p>
    </div>
    <div class="carte">
      <h3>Vu par le backend (GET /api/me, via la gateway)</h3>
      @if (moi(); as m) {
        <p class="meta">userId (claim sub) : <code>{{ m.userId }}</code></p>
        <p class="meta">rôles : {{ m.roles.join(', ') }}</p>
      } @else {
        <p class="vide">Chargement…</p>
      }
    </div>
  `,
})
export class ProfilComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  protected readonly moi = signal<Moi | null>(null);

  ngOnInit(): void {
    this.http.get<Moi>(`${config.apiUrl}/me`).subscribe({ next: m => this.moi.set(m), error: () => undefined });
  }
}
