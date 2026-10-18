# CLAUDE.md — PetitesAnnonces

Référence de travail du projet. Source de vérité : [`docs/enonce.pdf`](docs/enonce.pdf). En cas de doute, l'énoncé prime.

---

## 1. Résumé de l'énoncé

### Objectif
Application web d'annonces dont **toute la sécurité est centralisée sur une API Gateway**. Démarrage en une
commande : `docker compose up`. Fonctionnel minimal, enjeu technique : le parcours d'un jeton émis par un IdP
à travers une architecture distribuée.

**Principe : Keycloak authentifie, la gateway contrôle, le backend autorise.**
Le token ne dépasse jamais la gateway ; en aval, l'identité circule uniquement en headers (`X_USER_ID`, `X_ROLE_ID`)
sur un réseau que le client ne peut pas atteindre.

### Services, ports, réseaux

| Service | Technologie | Port hôte | Réseaux |
|---|---|---|---|
| `frontend` | Angular 17+ servi par Nginx | 4200 | public |
| `gateway` | Kong 3.x OSS, DB-less (port interne 8000) | 8080 | public, internal |
| `keycloak` | Keycloak 25+ (`start-dev`) | 8180 | public, internal |
| `backend` | Spring Boot 3, Java 21, port 8081 | **aucun** | internal |
| `postgres` | PostgreSQL 16 (bases `keycloak` + `annonces`) | **aucun** | internal |

Admin API de Kong (8001) : **jamais publiée**.

### Déroulé d'un appel
1. Angular redirige vers Keycloak avec `code_challenge` (PKCE, `S256`).
2. Keycloak renvoie un `code` ; Angular l'échange sur `/token` avec le `code_verifier` → access + refresh token.
3. Angular appelle la gateway avec `Authorization: Bearer <access_token>`.
4. Kong vérifie la signature (clé publique RSA du realm **figée dans kong.yml**), `iss` et `exp`, puis rate limiting.
5. Kong supprime les headers d'identité forgés et `Authorization`, ajoute `X_USER_ID` et `X_ROLE_ID`.
6. Le backend construit l'`Authentication`, `@PreAuthorize` autorise ou refuse, accès PostgreSQL.

### Rôles (realm roles Keycloak, composites)
| Rôle | Droits | Composite de |
|---|---|---|
| `USER` | consulter les annonces publiées, créer, modifier/supprimer **ses** annonces, lister ses annonces | — |
| `MODERATEUR` | lecture USER + lister les annonces en attente, publier/rejeter, supprimer n'importe quelle annonce | USER |
| `ADMIN` | tout MODERATEUR + créer/supprimer des catégories | MODERATEUR (+ USER) |

Utilisateurs de test : `alice`, `bob` (USER), `modo` (MODERATEUR), `admin` (ADMIN).

### Modèle de données
| Entité | Champs | Règles |
|---|---|---|
| `Annonce` | `id` (UUID), `titre`, `description`, `prix`, `categorieId`, `statut`, `auteurId`, `dateCreation`, `dateModification` | titre 5–100 car., description ≤ 2000, prix ≥ 0, `auteurId` = `X_USER_ID`, **jamais fourni par le client** |
| `Categorie` | `id`, `libelle` | libellé unique, 2–50 car. ; 4 initiales : Immobilier, Véhicules, Emploi, Multimédia |

Cycle de vie : `EN_ATTENTE` → `PUBLIEE` ou `REJETEE`. Toute modification par l'auteur → retour à `EN_ATTENTE`.

### Endpoints (via la gateway, sous `/api`) — tous authentifiés
| Méthode | Chemin | Rôle | Comportement |
|---|---|---|---|
| GET | `/api/me` | authentifié | `userId` + `roles` lus dans le contexte Spring Security |
| GET | `/api/annonces` | USER | annonces `PUBLIEE`, paginées (`page`, `size` ≤ 50), filtre `categorieId` optionnel |
| GET | `/api/annonces/{id}` | USER | publiée, ou non publiée si auteur ou MODERATEUR ; sinon **404** |
| GET | `/api/annonces/mes-annonces` | USER | annonces de l'utilisateur courant, tous statuts |
| POST | `/api/annonces` | USER | crée en `EN_ATTENTE`, **201 + Location** |
| PUT | `/api/annonces/{id}` | USER + auteur | modifie, statut → `EN_ATTENTE` |
| DELETE | `/api/annonces/{id}` | auteur, MODERATEUR ou ADMIN | **204** |
| GET | `/api/moderation/annonces` | MODERATEUR | `EN_ATTENTE`, plus anciennes d'abord |
| PATCH | `/api/moderation/annonces/{id}/publier` | MODERATEUR | → `PUBLIEE` |
| PATCH | `/api/moderation/annonces/{id}/rejeter` | MODERATEUR | → `REJETEE`, corps `{ "motif": "…" }` **obligatoire** |
| GET | `/api/categories` | USER | liste |
| POST | `/api/categories` | ADMIN | crée |
| DELETE | `/api/categories/{id}` | ADMIN | supprime si aucune annonce, sinon **409** |

### Exigences par composant

**4.1 Docker Compose**
- `docker-compose.yml` à la racine, 5 services ; réseaux `public` (frontend, gateway, keycloak) et `internal` (gateway, backend, postgres, keycloak).
- Ports publiés : 4200, 8080 (→ 8000 Kong), 8180. Backend, PostgreSQL, Admin API Kong : aucun port.
- `healthcheck` sur chaque service (Kong : `kong health`) ; `depends_on` avec `condition: service_healthy`.
- Secrets dans `.env` (fourni en `.env.example`, `.env` ignoré par Git).
- Images applicatives : Dockerfile **multi-stage** (build Maven / npm → image d'exécution légère). Kong : image officielle, sans build.

**4.2 PostgreSQL**
- PostgreSQL 16, bases `keycloak` et `annonces` créées par `postgres/init.sql`, volume nommé.
- Schéma `annonces` géré par **Flyway** (migrations versionnées + catégories initiales).

**4.3 Keycloak**
- Keycloak 25+, `start-dev`, base PostgreSQL, realm importé au démarrage depuis `keycloak/realm-annonces.json`.
- Realm `annonces`, rôles de realm `USER`, `MODERATEUR`, `ADMIN` composites.
- Client `annonces-front` : **public**, Standard flow seul (implicit + direct access grants désactivés), **PKCE S256 obligatoire**,
  redirect URI `http://localhost:4200/*`, web origin `http://localhost:4200`.
- Access token : **5 minutes**.
- Paire de clés RSA **fixe** dans le realm exporté. `KC_HOSTNAME` fixé pour que `iss` = `http://localhost:8180/realms/annonces`.

**4.4 Frontend Angular**
- Angular 17+, servi en prod par Nginx.
- Authorization Code + PKCE avec `angular-oauth2-oidc` ou `keycloak-angular`.
- Tokens en mémoire ou `sessionStorage`, **jamais `localStorage`**.
- `HttpInterceptor` : `Authorization: Bearer` **uniquement** vers la gateway.
- Renouvellement avant expiration via le refresh token ; déconnexion via `end_session` de Keycloak.
- Écrans : liste, détail, mes annonces, formulaire création/modification, modération (MODERATEUR), catégories (ADMIN).
- Menus et routes masqués selon les rôles (guards) — confort d'affichage, pas une sécurité.
- Erreurs : 401 → login ; 403 → « accès refusé » ; 429 → délai lu dans `Retry-After`.

**4.5 Kong OSS DB-less** (`kong/kong.yml` chargé via `KONG_DECLARATIVE_CONFIG`)
1. Service `annonces-backend` (`http://backend:8081`) + route `/api`.
2. Plugin `cors` : origine `http://localhost:4200` seule, méthodes GET/POST/PUT/PATCH/DELETE, header `Authorization`,
   expose `Retry-After`. Les preflight `OPTIONS` sont traités avant toute authentification et tout décompte.
3. Plugin `jwt` : `key_claim_name: iss`, `claims_to_verify: [exp]` ; consumer `keycloak-annonces` avec credential jwt :
   `key` = `http://localhost:8180/realms/annonces`, `algorithm: RS256`, `rsa_public_key` = clé publique PEM du realm.
   Token absent, mal signé, expiré ou d'un autre émetteur → 401.
4. Plugin Lua maison `identity-headers` (`KONG_PLUGINS=bundled,identity-headers`, monté par volume),
   priorité **entre 1450 (jwt) et 910 (rate-limiting)**. Il :
   supprime `X_USER_ID`/`X_ROLE_ID` venant du client ; `X_USER_ID` = claim `sub` ;
   `X_ROLE_ID` = `realm_access.roles` ∩ {USER, MODERATEUR, ADMIN}, séparés par des virgules ; supprime `Authorization`.
5. Plugin `rate-limiting` : `minute: 3`, `policy: local`, `limit_by: header`, `header_name: X_USER_ID`
   → par utilisateur, sur tout `/api/**`, 429 + `Retry-After`.
6. Admin API (8001) non publiée.

**4.6 Backend Spring Boot 3**
- Java 21, Spring Web, Security, Data JPA, Bean Validation, Flyway.
- **Aucune dépendance OAuth2 / JWT / Keycloak.** Le backend n'appelle **jamais** Keycloak.
- `OncePerRequestFilter` : lit `X_USER_ID`/`X_ROLE_ID` → `Authentication` (principal = userId, autorités = `ROLE_` + rôle)
  → `SecurityContextHolder`. Headers absents ou vides → 401.
- Stateless : pas de session, pas de form login, CSRF désactivé.
- `@EnableMethodSecurity` + `@PreAuthorize` (ex. `hasRole('MODERATEUR')`), propriété par expression
  (ex. `@PreAuthorize("@annonceSecurity.estAuteur(#id, authentication)")`). Pas de `if` sur les rôles dans le métier.
- Erreurs JSON `ProblemDetail` (RFC 9457) : 400, 401, 403, 404, 409.
- Tests d'intégration `@SpringBootTest` + Testcontainers PostgreSQL, headers injectés directement.

### Pièges classiques
- Le plugin `jwt` de Kong OSS ne lit pas le JWKS : la clé publique est figée dans `kong.yml` → le realm exporté doit
  embarquer une **paire RSA fixe**, sinon Keycloak en régénère une et tous les tokens sont rejetés.
- `iss` = URL vue par le navigateur (`http://localhost:8180/...`), pas `keycloak:8080` → fixer `KC_HOSTNAME`.
- Plugin `openid-connect` = Kong Enterprise → **interdit**.
- Nginx (sous Kong) ignore par défaut les headers client contenant `_`, mais le plugin doit quand même les supprimer.
- _Ajoutés par nous, à vérifier aux étapes concernées :_
  - Image Keycloak sans `curl` : healthcheck via `/dev/tcp` sur le port de management 9000 (`KC_HEALTH_ENABLED=true`).
  - Flyway 10+ avec PostgreSQL : dépendance `flyway-database-postgresql` nécessaire.
  - Docker Engine 29 : vérifier que la version de Testcontainers gérée par Spring Boot est compatible (API Docker minimale) ; sinon la surcharger.
  - `rate-limiting` avec `limit_by: header` : si le header manque, Kong se rabat sur l'IP → `identity-headers` doit tourner avant (priorité).
  - Le 429 et le 401 générés par Kong doivent eux aussi porter les headers CORS, sinon le front ne lit pas `Retry-After`.
  - Le port 8080 est utilisé par l'appli `demofares` lancée dans IntelliJ → l'arrêter avant `docker compose up`.
  - Un PostgreSQL tourne dans la distribution WSL **Ubuntu** et écoute sur `127.0.0.1:5432` (relayé par `wslrelay.exe`) :
    un test du port 5432 depuis Windows répond donc, même stack arrêtée. Pour DEP-02, prouver avec
    `docker compose ps` (colonne ports = `5432/tcp`, sans `0.0.0.0:`) ou arrêter ce PostgreSQL WSL avant la démo
    (`wsl -d Ubuntu -- sudo service postgresql stop`).
  - `postgres/init.sql` ne s'exécute qu'au premier démarrage (volume vide) : après modification → `docker compose down -v`.

### Livrables
- Arborescence : `frontend/`, `backend/`, `kong/kong.yml`, `kong/plugins/identity-headers/` (`handler.lua`, `schema.lua`),
  `keycloak/realm-annonces.json`, `postgres/init.sql`, `docker-compose.yml`, `.env.example`.
- `README.md` : démarrage en une commande, comptes de test, schéma d'architecture, choix techniques justifiés,
  procédure d'extraction de la clé publique du realm.
- Collection Bruno/Postman ou script `curl` couvrant **GW, RL, MT**.
- Tests automatisés backend (**BK, MT**) + script de test gateway (token absent, expiré, forgé, rate limit).
- Commits réguliers, messages explicites. Aucune règle d'autorisation front ne remplace une règle serveur.
- Bonus (≤ 10 pts) : rate limiting Redis partagé entre 2 Kong ; vérif `azp` dans `identity-headers` ; secret partagé/mTLS
  Kong→backend ; génération auto de `kong.yml` depuis le JWKS ; `correlation-id` repris dans les logs backend.
- Seuil : 70/100 avec **GW-01→GW-07 et BK-01→BK-03 obligatoires**.

---

## 2. Critères d'acceptation (48)

### 5.1 Déploiement (15 pts)
| ID | Étant donné | Quand | Alors |
|---|---|---|---|
| DEP-01 | Docker + dépôt cloné, `.env` copié de `.env.example` | `docker compose up -d --build` | 5 conteneurs `healthy` en < 3 min, sans action manuelle |
| DEP-02 | Stack démarrée | appel `localhost:8081` ou port 5432 depuis l'hôte | connexion échoue |
| DEP-03 | Stack démarrée | ouvrir `localhost:8180` | console Keycloak : realm `annonces` importé avec clients, rôles, utilisateurs |
| DEP-04 | Annonces créées | `docker compose down` puis `up` | annonces et config Keycloak conservées |
| DEP-05 | Le dépôt | chercher un mot de passe en clair | aucun secret commité hors `.env.example` (valeurs factices) |

### 5.2 Keycloak et PKCE (15 pts)
| ID | Étant donné | Quand | Alors |
|---|---|---|---|
| KC-01 | Non connecté | ouvre `localhost:4200` | redirigé vers le login Keycloak du realm `annonces` |
| KC-02 | Redirection | inspecter l'URL d'autorisation | `response_type=code`, `code_challenge`, `code_challenge_method=S256` |
| KC-03 | Retour sur l'app | inspecter le réseau | échange du code sur `/token` avec `code_verifier` ; aucun token dans l'URL |
| KC-04 | Client `annonces-front` | `grant_type=password` ou implicit | refusé : Standard flow seul |
| KC-05 | `modo` connecté | décoder l'access token | `realm_access.roles` contient MODERATEUR et USER |
| KC-06 | Connecté | « Se déconnecter » | session Keycloak fermée ; retour sur l'app → login redemandé |

### 5.3 Frontend (15 pts)
| ID | Étant donné | Quand | Alors |
|---|---|---|---|
| FR-01 | alice connectée | consulte les annonces | chaque appel gateway porte `Bearer` ; aucun appel Keycloak ne le porte inutilement |
| FR-02 | alice connectée | inspecter `localStorage` | aucun token |
| FR-03 | alice (USER) | affiche le menu | pas de « Modération »/« Catégories » ; `/moderation` saisi à la main → redirigé |
| FR-04 | alice connectée > 5 min | continue de naviguer | token renouvelé sans retour au login |
| FR-05 | Réponse 429 | affichage | message avec secondes à attendre lues dans `Retry-After` |
| FR-06 | Réponse 403 | affichage | « accès refusé », sans déconnexion |

### 5.4 Gateway : authentification et headers (20 pts)
| ID | Étant donné | Quand | Alors |
|---|---|---|---|
| GW-01 | Pas de `Authorization` | `GET /api/annonces` | 401, n'atteint pas le backend (logs) |
| GW-02 | Signature altérée | appel | 401 |
| GW-03 | Token expiré (> 5 min) | appel | 401 |
| GW-04 | Token d'un autre realm/Keycloak | appel | 401 : `iss` ne correspond à aucun credential |
| GW-05 | Token valide d'alice | `GET /api/me` | 200, `userId` = sub d'alice, `roles` = `["USER"]` |
| GW-06 | Token d'alice + `X_USER_ID` forgé (sub de bob) | `GET /api/me` | sub d'alice : header forgé supprimé |
| GW-07 | Token d'alice + `X_ROLE_ID: ADMIN` forgé | `POST /api/categories` | 403 |
| GW-08 | Requête valide | log debug backend | contient `X_USER_ID`, `X_ROLE_ID`, pas `Authorization` |
| GW-09 | Origine `http://evil.local` | preflight | pas de `Access-Control-Allow-Origin` pour cette origine |
| GW-10 | Stack démarrée | `localhost:8001` depuis l'hôte | connexion échoue |
| GW-11 | Le dépôt | config Kong | tout dans `kong/kong.yml`, plugin dans `kong/plugins/`, uniquement plugins OSS |

### 5.5 Rate limiting (10 pts)
| ID | Étant donné | Quand | Alors |
|---|---|---|---|
| RL-01 | alice authentifiée | 3 appels < 1 min | OK |
| RL-02 | alice a fait 3 appels | 4ᵉ appel, n'importe quel `/api/**` | 429 + `Retry-After` (s) ; backend non appelé |
| RL-03 | alice a reçu 429 | attend le délai puis rappelle | OK |
| RL-04 | alice bloquée | bob appelle | bob non bloqué (compteur par utilisateur) |
| RL-05 | alice bloquée | preflight `OPTIONS` | répond sans être décompté |

### 5.6 Backend : contexte Spring Security (10 pts)
| ID | Étant donné | Quand | Alors |
|---|---|---|---|
| BK-01 | Code backend | `pom.xml` | aucune dépendance `spring-boot-starter-oauth2-*`, `jjwt`, `nimbus`, Keycloak |
| BK-02 | Requête sans `X_USER_ID` (test d'intégration) | atteint un contrôleur | 401 |
| BK-03 | `X_USER_ID: u1`, `X_ROLE_ID: MODERATEUR,USER` | lire `SecurityContextHolder` | principal `u1`, autorités `ROLE_MODERATEUR`, `ROLE_USER` |
| BK-04 | Endpoints §3 | lire le code | chaque règle via `@PreAuthorize`, pas de `if` sur les rôles |
| BK-05 | Deux requêtes d'utilisateurs différents | comparer les contextes | aucune fuite, contexte vidé, aucune session |

### 5.7 Règles métier (10 pts)
| ID | Étant donné | Quand | Alors |
|---|---|---|---|
| MT-01 | alice | crée une annonce valide | 201, `EN_ATTENTE`, `auteurId` = son sub même si le corps en envoie un autre |
| MT-02 | Annonce `EN_ATTENTE` d'alice | bob `GET /api/annonces/{id}` | 404 |
| MT-03 | Annonce d'alice | bob `PUT` ou `DELETE` | 403 |
| MT-04 | Annonce `PUBLIEE` d'alice | alice la modifie | 200, `EN_ATTENTE`, disparaît de la liste publique |
| MT-05 | alice (USER) | `PATCH /api/moderation/annonces/{id}/publier` | 403 |
| MT-06 | modo | publie une annonce en attente | 200, apparaît dans `GET /api/annonces` |
| MT-07 | modo | rejette sans motif | 400 `ProblemDetail` |
| MT-08 | modo (MODERATEUR) | `POST /api/categories` | 403 ; même appel par admin → 201 |
| MT-09 | Catégorie utilisée | admin la supprime | 409 |
| MT-10 | Titre de 3 car. ou prix négatif | création | 400 détaillant le champ |

Grille : DEP 15 · KC 15 · FR 15 · GW 20 · RL 10 · BK 10 · MT 10 · Qualité 5 = 100.

---

## 3. Environnement détecté (18/10/2026)

| Outil | Version détectée | Exigé | Décision |
|---|---|---|---|
| OS | Windows 10 Pro 22H2 (10.0.19045) x64 | — | scripts en bash (Git Bash) + équivalents PowerShell si besoin |
| Java | JAVA_HOME = JDK 17.0.12 ; **Corretto 21.0.4** dans `~/.jdks/corretto-21.0.4` | Java 21 | utiliser Corretto 21 pour le backend (JAVA_HOME de session / SDK IntelliJ) ; image Docker Temurin 21 |
| Maven | 3.9.9 | — | + wrapper `mvnw` dans `backend/` |
| Node / npm | 22.15.0 / 10.9.2 | — | OK pour Angular 19 |
| Angular CLI | 19.2.11 (global) | Angular 17+ | **Angular 19** |
| Docker / Compose | 29.4.0 / v5.1.2, démon Linux, 8 CPU, ~7,7 Go | — | OK |
| Git | 2.45.2 | — | OK |
| Ports | 4200 libre, 8180 libre, **8080 occupé** (appli demofares IntelliJ) | 4200/8080/8180 | arrêter demofares avant les tests Kong |

Choix de versions (tags exacts vérifiés/épinglés à l'étape qui les introduit) :
- Backend : **Spring Boot 3.5.x**, Java 21 ; build `maven:3.9-eclipse-temurin-21`, runtime `eclipse-temurin:21-jre-alpine`.
- Frontend : **Angular 19** + **`angular-oauth2-oidc`** ; build `node:22-alpine`, runtime `nginx:*-alpine`.
- Keycloak : `quay.io/keycloak/keycloak:26.x`. Kong : `kong:3.9` (dernière ligne OSS 3.x, à confirmer). PostgreSQL : `postgres:16-alpine`.

---

## 4. Règles de travail

1. **Une seule étape à la fois** :
   - expliquer brièvement ce qu'on fait et pourquoi ;
   - écrire le code/la config de cette étape uniquement, directement dans le projet ;
   - lancer soi-même les vérifications (build, tests, docker compose, curl) et donner les commandes pour les refaire ;
   - commit avec un message explicite ;
   - cocher l'étape ci-dessous ;
   - **s'arrêter** et attendre « ok, étape suivante » ou des questions.
2. Ne jamais passer à l'étape suivante sans accord.
3. Contraintes strictes :
   - aucun secret commité ;
   - aucune dépendance OAuth2, JWT ou Keycloak dans le backend ;
   - plugin `openid-connect` de Kong interdit ;
   - paire de clés RSA fixe dans le realm exporté ;
   - `KC_HOSTNAME` fixé pour que `iss` = `http://localhost:8180/realms/annonces` ;
   - Admin API de Kong non publiée ;
   - tokens jamais en `localStorage`.
4. En cas d'ambiguïté : poser la question plutôt que deviner.
5. Réponses en français.

---

## 5. Plan

Chaque étape est testable en moins d'une heure. Critères entre crochets = critères couverts (✱ = partiellement).

### Phase A — Socle
- [x] **1. Initialisation** — dossier, `git init`, structure, `.gitignore`, `.env.example`, README, CLAUDE.md. [DEP-05✱]
- [x] **2. PostgreSQL** — `docker-compose.yml` (réseaux `public`/`internal`, volume nommé), service `postgres:16`,
      `postgres/init.sql` (bases + utilisateurs `keycloak` et `annonces`), healthcheck `pg_isready`.
      _Test : `docker compose up -d postgres` → healthy ; `psql \l` ; port 5432 fermé depuis l'hôte._ [DEP-02✱, DEP-05✱]

### Phase B — Keycloak
- [ ] **3. Keycloak démarré** — service `keycloak` `start-dev` sur PostgreSQL, `KC_HOSTNAME=http://localhost:8180`,
      admin bootstrap via `.env`, healthcheck, `depends_on: service_healthy`.
      _Test : console sur `localhost:8180`, `.well-known/openid-configuration` du master._ [DEP-03✱]
- [ ] **4. Realm `annonces`** — `realm-annonces.json` importé : rôles composites, client `annonces-front`
      (public, standard flow seul, PKCE S256), token 5 min, 4 utilisateurs.
      _Test : discovery `iss` = `http://localhost:8180/realms/annonces` ; `grant_type=password` refusé ; console._ [DEP-03, KC-04]
- [ ] **5. Paire RSA fixe + script de token** — génération d'une paire RSA, composant `rsa` dans le realm, extraction du PEM
      public (procédure documentée) ; script `scripts/get-token.sh` qui réalise le flux code + PKCE en curl.
      _Test : `docker compose down -v && up` → même `kid`/clé dans le JWKS ; token de modo décodé._ [KC-05, DEP-04✱]

### Phase C — Backend
- [ ] **6. Squelette backend** — projet Spring Boot 3.5 / Java 21 (Web, Security, JPA, Validation, Flyway, Actuator),
      migrations Flyway V1 (schéma + 4 catégories), Dockerfile multi-stage, service `backend` sur `internal` uniquement.
      _Test : `mvnw verify` ; conteneur healthy ; `localhost:8081` injoignable._ [BK-01, DEP-02]
- [ ] **7. Sécurité par headers** — `OncePerRequestFilter`, config stateless, `@EnableMethodSecurity`, 401/403 en
      `ProblemDetail`, `GET /api/me`, log debug des headers reçus. Tests Testcontainers.
      _Test : tests d'intégration verts._ [BK-02, BK-03, BK-05, GW-08✱]
- [ ] **8. Catégories** — entité, repo, service, contrôleur, `@PreAuthorize`, validation, 409 si utilisée.
      _Test : tests d'intégration._ [MT-08, MT-09]
- [ ] **9. Annonces (côté auteur)** — CRUD, `mes-annonces`, pagination + filtre, `@annonceSecurity.estAuteur`,
      règles 404, 201 + Location, statut remis en attente.
      _Test : tests d'intégration._ [MT-01, MT-02, MT-03, MT-04, MT-10, BK-04✱]
- [ ] **10. Modération** — liste en attente, publier, rejeter avec motif obligatoire, gestionnaire d'erreurs complet (400/404/409).
      _Test : tests d'intégration._ [MT-05, MT-06, MT-07, BK-04]

### Phase D — Gateway Kong
- [ ] **11. Kong DB-less de base** — `kong/kong.yml` (service `annonces-backend`, route `/api`), image officielle,
      healthcheck `kong health`, Admin API non publiée.
      _Test : `curl localhost:8080/api/me` → 401 renvoyé par le backend ; `localhost:8001` injoignable._ [GW-10, GW-11✱]
- [ ] **12. Plugin `jwt`** — consumer `keycloak-annonces`, credential RS256 avec la clé publique PEM.
      _Test : token absent / altéré / expiré / autre émetteur → 401 ; valide → passe._ [GW-01, GW-02, GW-03, GW-04]
- [ ] **13. Plugin Lua `identity-headers`** — `handler.lua` + `schema.lua`, priorité ~1000, montage par volume.
      _Test : `/api/me` d'alice ; headers forgés ignorés ; logs backend sans `Authorization`._ [GW-05, GW-06, GW-07, GW-08, GW-11]
- [ ] **14. CORS + rate limiting** — plugins `cors` et `rate-limiting`.
      _Test : preflight evil.local ; 4ᵉ appel → 429 + `Retry-After` ; bob non bloqué ; OPTIONS non décomptés._
      [GW-09, RL-01→RL-05]
- [ ] **15. Script de test gateway + collection** — `scripts/test-gateway.sh` (et/ou collection Bruno) couvrant GW, RL, MT.
      _Test : exécution complète du script._ [livrable]

### Phase E — Frontend
- [ ] **16. Squelette Angular + Nginx** — app Angular 19, Dockerfile multi-stage, conf Nginx (fallback SPA), service `frontend`, healthcheck.
      _Test : `localhost:4200` sert l'app._ [DEP-01✱]
- [ ] **17. Authentification OIDC** — `angular-oauth2-oidc` : code + PKCE, `sessionStorage`, login forcé, refresh automatique,
      logout `end_session`.
      _Test : DevTools (URL d'autorisation, `/token`, storage), attente > 5 min._ [KC-01, KC-02, KC-03, KC-06, FR-02, FR-04]
- [ ] **18. Interceptor + erreurs** — Bearer vers la gateway uniquement, gestion 401/403/429, page « Mon profil » (`/api/me`).
      _Test : onglet réseau ; provoquer 403 et 429._ [FR-01, FR-05, FR-06]
- [ ] **19. Écrans annonces** — liste paginée + filtre, détail, mes annonces, formulaire création/modification.
      _Test : parcours alice dans le navigateur._ [MT-01…MT-04 via UI]
- [ ] **20. Modération, catégories, guards** — écrans MODERATEUR/ADMIN, menus et routes selon les rôles.
      _Test : alice / modo / admin._ [FR-03, MT-06, MT-08 via UI]

### Phase F — Finalisation
- [ ] **21. Recette complète** — `docker compose down -v` puis `up -d --build` chronométré, persistance, recherche de secrets,
      passage des 48 critères.
      _Test : checklist des critères._ [DEP-01, DEP-04, DEP-05, tous]
- [ ] **22. README final** — schéma d'architecture, comptes de test, choix justifiés, procédure d'extraction de la clé publique.
- [ ] **23. (Optionnel) Bonus** — vérification `azp`, `correlation-id`, secret partagé Kong→backend, etc.

### Questions ouvertes (à trancher avant l'étape concernée)
- **Q1 (étapes 4–5) — secrets du realm** : la clé privée RSA et les mots de passe des utilisateurs de test sont des secrets,
  mais DEP-01 exige que `.env` copié depuis `.env.example` fonctionne sans action manuelle.
- **Q2 (étape 5) — obtention de tokens pour les tests curl** : le password grant est interdit sur `annonces-front` (KC-04).
