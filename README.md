# PetitesAnnonces

Application d'annonces dont **toute la sécurité est centralisée sur une API Gateway** :
Keycloak **authentifie**, Kong **contrôle**, le backend **autorise**. Le token ne dépasse jamais la gateway.

Énoncé : [`docs/enonce.pdf`](docs/enonce.pdf) · suivi du projet et critères : [`CLAUDE.md`](CLAUDE.md)

## Démarrage en une commande

```bash
cp .env.example .env          # PowerShell : Copy-Item .env.example .env
docker compose up -d --build --wait
```

| Service | URL | Rôle |
|---|---|---|
| Application | http://localhost:4200 | SPA Angular (redirige vers le login Keycloak) |
| API Gateway | http://localhost:8080/api | Kong OSS, seul point d'entrée de l'API |
| Keycloak | http://localhost:8180 | Identity Provider, console d'administration |

Le backend (8081), PostgreSQL (5432) et l'Admin API de Kong (8001) **ne sont pas publiés**.

> Durée : ~2 min quand les images sont en cache. Le tout premier build télécharge les dépendances Maven et npm
> (plus long) : pour une démonstration, préconstruire avec `docker compose build`.
> Les ports 4200, 8080 et 8180 doivent être libres sur l'hôte.

## Comptes de test (realm `annonces`)

Mots de passe dans `.env` (valeurs de démo de `.env.example` : `<utilisateur>-demo`).

| Utilisateur | Rôle attribué | Rôles effectifs (composites Keycloak) | Peut |
|---|---|---|---|
| `alice` | USER | USER | consulter, publier, modifier/supprimer ses annonces |
| `bob` | USER | USER | idem |
| `modo` | MODERATEUR | MODERATEUR, USER | + file de modération, publier/rejeter, supprimer toute annonce |
| `admin` | ADMIN | ADMIN, MODERATEUR, USER | + créer/supprimer des catégories |

Console Keycloak : compte `KC_BOOTSTRAP_ADMIN_*` du `.env`.

## Architecture

```
            ┌────────────────────── réseau "public" ──────────────────────┐
            │                                                             │
 Navigateur │  ① login : Authorization Code + PKCE (S256)                 │
 ─────────► │  frontend :4200 ───────────────────────────► keycloak :8180 │
 (SPA)      │  (Nginx)                                     (realm annonces,
            │     │ ② Authorization: Bearer <access_token>   paire RSA fixe)
            │     ▼                                             │         │
            │  gateway :8080  (Kong OSS DB-less, kong/kong.yml) │         │
            └─────│─────────────────────────────────────────────│─────────┘
                  │ cors → jwt (signature RS256, iss, exp)      │
                  │ → identity-headers (Lua) → rate-limiting    │
            ┌─────│──────────── réseau "internal" (aucun port publié) ──────┐
            │     ▼ ③ X_USER_ID, X_ROLE_ID — sans Authorization │           │
            │  backend :8081 (Spring Boot 3)  ──SQL──►  postgres (bases     │
            │  filtre headers → @PreAuthorize            annonces, keycloak)│
            └──────────────────────────────────────────────────────────────┘
```

1. Angular redirige vers Keycloak avec un `code_challenge` (S256), échange le `code` + `code_verifier` contre les tokens.
2. Chaque appel à l'API porte `Authorization: Bearer …`, et **seulement** les appels vers la gateway.
3. Kong vérifie la signature avec la clé publique du realm figée dans `kong.yml`, l'émetteur (`iss`) et l'expiration (`exp`),
   supprime les headers d'identité éventuellement forgés et `Authorization`, pose `X_USER_ID` (claim `sub`) et
   `X_ROLE_ID` (rôles du realm), puis compte 3 appels / minute / utilisateur.
4. Le backend reconstruit l'`Authentication` Spring Security depuis ces headers ; `@PreAuthorize` autorise ou refuse.

## Choix techniques

| Sujet | Choix | Pourquoi |
|---|---|---|
| Versions | Java 21, Spring Boot 3.5.16, Angular 19.2, Keycloak 26.8.1, Kong 3.9.4 OSS, PostgreSQL 16 | dernières versions compatibles avec les contraintes de l'énoncé, toutes épinglées |
| Librairie OIDC | `angular-oauth2-oidc` | standard OIDC sans dépendance à Keycloak ; chaque étape du flux (discovery, PKCE, refresh) reste explicite |
| Stockage des tokens | `sessionStorage` | jamais `localStorage` ; perdus à la fermeture de l'onglet |
| Renouvellement | refresh token à 75 % de la durée de vie | pas d'iframe de « silent refresh », compatible avec un token de 5 min |
| Rôles dans le front | lus dans l'access token | aucun appel API pour construire les menus (quota de 3 requêtes / minute) ; simple confort d'affichage |
| 429 côté front | compte à rebours lu dans `Retry-After`, GET rejoué automatiquement | l'écran se remplit seul ; les écritures ne sont jamais rejouées |
| Secrets du realm | placeholders `${…}` dans `realm-annonces.json`, valeurs dans `.env` | aucun secret dans le realm versionné ; démarrage sans action manuelle |
| Clé de signature | paire RSA **fixe** importée avec le realm | le plugin `jwt` de Kong OSS ne lit pas le JWKS : la clé ne doit jamais changer |
| `KC_HOSTNAME` | `http://localhost:8180` | `iss` identique vu du navigateur et du réseau Docker, égal à la `key` du credential Kong |
| Plugin maison | `identity-headers` (Lua, priorité 1000) | entre `jwt` (1450) et `rate-limiting` (910), qui compte par `X_USER_ID` ; vérifie aussi `azp` (bonus) |
| CORS | origine en regex ancrée `^http://localhost:4200$` | avec une origine unique en texte simple, Kong la renverrait à toutes les origines |
| `KONG_PORT_MAPS` | `8080:8000` | `X-Forwarded-Port` correct, donc `Location` publique sur les 201 |
| Authentification backend | `PreAuthenticatedAuthenticationToken` posé par un `OncePerRequestFilter` | l'identité a été vérifiée en amont ; aucune dépendance OAuth2/JWT/Keycloak |
| Autorisation | `@PreAuthorize` partout, bean `@annonceSecurity.estAuteur(...)` | aucune règle de rôle dans le code métier ; annonce non visible → 404 via `@PostAuthorize` + `@HandleAuthorizationDenied` |
| Erreurs | `ProblemDetail` (RFC 9457) | 400 avec le détail par champ, 401, 403, 404, 409 |
| Schéma | Flyway (V1 schéma, V2 catégories, V3 unicité insensible à la casse), Hibernate en `validate` | contraintes métier aussi garanties par la base |

## Bonus réalisés

| Bonus | Mise en œuvre | Vérification |
|---|---|---|
| Vérification du claim `azp` | `identity-headers` refuse (401) un token du realm non émis pour `annonces-front` | `forge-jwt.ps1 -RealmKey -Azp autre-client` → 401 |
| Traçabilité `correlation-id` | plugin Kong `correlation-id` (renvoyé au client, même sur 401/429) ; `CorrelationIdFilter` met l'identifiant dans le MDC, `[cid=…]` sur chaque ligne de log du backend | `X-Correlation-ID` d'une réponse → `docker compose logs backend \| Select-String "<id>"` |
| Secret partagé Kong → backend | `identity-headers` pose `X-Gateway-Secret` (valeur lue par Kong via `{vault://env/gateway-secret}`, jamais écrite dans `kong.yml`) ; `GatewaySecretFilter` refuse tout `/api` sans ce secret (403), comparaison en temps constant | un conteneur du réseau `internal` qui forge `X_USER_ID` sans le secret → 403 |

Non réalisés : rate limiting partagé via Redis entre deux Kong, génération automatique de `kong.yml` depuis le JWKS.

## Clé de signature du realm

Le plugin `jwt` de Kong OSS ne lit pas le JWKS : la clé publique du realm est **figée** dans `kong/kong.yml`.
Le realm embarque donc une **paire RSA fixe** (composant `rsa-fixe-annonces`), dont la clé privée est injectée à l'import
depuis `REALM_RSA_PRIVATE_KEY` (`.env`). Sans cela, Keycloak générerait une nouvelle paire à chaque création du realm,
et Kong rejetterait tous les tokens.

### Extraire la clé publique du realm (format PEM)

L'endpoint public `http://localhost:8180/realms/annonces` renvoie la clé en base64 (`public_key`) ; il suffit de l'encadrer
des en-têtes PEM, en lignes de 64 caractères :

```powershell
$k = (Invoke-RestMethod http://localhost:8180/realms/annonces).public_key
"-----BEGIN PUBLIC KEY-----`n" + (($k -split '(.{64})' | ? { $_ }) -join "`n") + "`n-----END PUBLIC KEY-----"
```

Autres moyens : console Keycloak → realm *annonces* → *Realm settings* → *Keys* → RS256 → *Public key* ;
ou hors ligne depuis la clé privée : `openssl pkey -in private.pem -pubout`.
Le résultat est versionné dans `keycloak/realm-public-key.pem` et recopié dans le credential de `kong/kong.yml`.

### Générer une nouvelle paire

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem
openssl pkey -in private.pem -pubout -out keycloak/realm-public-key.pem
grep -v -- ----- private.pem | tr -d '\n'     # -> valeur de REALM_RSA_PRIVATE_KEY (une ligne)
```

Mettre ensuite à jour `kong/kong.yml`, puis réimporter le realm (`docker compose down -v`).
La clé de `.env.example` est une **clé de démonstration** dédiée à ce projet local.

## Tests

```powershell
# Backend : 56 tests d'intégration (Testcontainers PostgreSQL 16), critères BK et MT
cd backend; $env:JAVA_HOME="<chemin d'un JDK 21>"; .\mvnw verify; cd ..

# Gateway de bout en bout : 27 vérifications GW-01→11, RL-01→05, MT-01→10 (~4 min, attentes du rate limiting)
.\scripts\test-gateway.ps1
.\scripts\test-gateway.ps1 -Sections GW,RL      # sans les règles métier
```

Outils :

```powershell
.\scripts\get-token.ps1 -User alice -Show                                   # vrai token via le flux code + PKCE
.\scripts\forge-jwt.ps1 -RealmKey -ExpiresIn -60                            # token expiré (GW-03)
.\scripts\forge-jwt.ps1 -Issuer "http://evil.local:8180/realms/annonces"    # autre émetteur (GW-04)
```

Le password grant est interdit sur `annonces-front` (KC-04) : `get-token.ps1` rejoue donc le flux Authorization Code + PKCE
(soumission du formulaire de login, contrôle du `state`, échange du code avec le `code_verifier`).

## Démonstration (repères)

| Critère | Comment le montrer |
|---|---|
| KC-01 à 03 | ouvrir http://localhost:4200, outils de développement → onglet Réseau : `code_challenge_method=S256`, `POST /token` |
| FR-03 | alice : pas de « Modération » ni de « Catégories » ; `/moderation` tapé à la main → retour à la liste |
| FR-05 | recharger 4 fois une page en moins d'une minute → bandeau avec compte à rebours |
| FR-06 | modo ouvre `/annonces/<id d'une annonce d'alice>/modifier` et enregistre → « Accès refusé », toujours connecté |
| GW-08 | `docker compose logs backend` → `X_USER_ID=…, X_ROLE_ID=…, Authorization=absent` |
| DEP-02 / GW-10 | `docker compose ps` : 8081, 5432 et 8001 non publiés |

Sur une machine de développement, d'autres programmes peuvent occuper ces ports (un PostgreSQL local sur 5432, une autre
application sur 8081) : `docker compose ps` fait foi pour montrer que la stack ne les publie pas.

## Déploiement continu

Pipeline : [`.github/workflows/ci-cd.yml`](.github/workflows/ci-cd.yml).

| Événement | Étapes |
|---|---|
| Pull request | `mvnw verify` (Testcontainers) → build des images backend et frontend (sans publication) |
| Push sur `main` | idem → images publiées sur `ghcr.io/<owner>/petites-annonces-{backend,frontend}` (`sha-<commit>` + `latest`) → déploiement SSH |

Le déploiement copie `docker-compose.yml`, [`docker-compose.prod.yml`](docker-compose.prod.yml), `kong/`, le realm et
`init.sql` dans `DEPLOY_PATH`, régénère le `.env` depuis les secrets GitHub, puis lance `docker compose pull` et
`docker compose up -d --wait`. Les images sont tirées avec le jeton éphémère du job : aucun jeton GHCR n'est stocké sur le serveur.

**Serveur** : Linux avec Docker Engine et Compose ≥ 2.24.4, un utilisateur membre du groupe `docker` et une clé SSH dédiée.

**Accès** : les URL de l'application restent `http://localhost:*` (`iss` des tokens, CORS, redirect URI). La surcharge de
production ne publie donc les ports que sur `127.0.0.1` du serveur. On y accède par tunnel SSH :

```bash
ssh -L 4200:localhost:4200 -L 8080:localhost:8080 -L 8180:localhost:8180 <user>@<serveur>
# puis http://localhost:4200 dans le navigateur local
```

Credentials à créer dans *Settings → Environments → `production`* :

| Type | Nom | Valeur |
|---|---|---|
| Secret | `DEPLOY_HOST` | IP ou nom DNS du serveur |
| Secret | `DEPLOY_USER` | utilisateur SSH (membre du groupe `docker`) |
| Secret | `DEPLOY_SSH_KEY` | clé **privée** SSH dédiée au déploiement (la publique dans `~/.ssh/authorized_keys` du serveur) |
| Secret | `DEPLOY_KNOWN_HOSTS` | sortie de `ssh-keyscan -p <port> <serveur>` (empreinte vérifiée du serveur) |
| Secret | `POSTGRES_PASSWORD` | mot de passe du superutilisateur PostgreSQL |
| Secret | `KEYCLOAK_DB_PASSWORD` | mot de passe de la base `keycloak` |
| Secret | `ANNONCES_DB_PASSWORD` | mot de passe de la base `annonces` |
| Secret | `KC_BOOTSTRAP_ADMIN_PASSWORD` | mot de passe de la console Keycloak |
| Secret | `REALM_ALICE_PASSWORD`, `REALM_BOB_PASSWORD`, `REALM_MODO_PASSWORD`, `REALM_ADMIN_PASSWORD` | mots de passe des comptes de test |
| Secret | `REALM_RSA_PRIVATE_KEY` | clé privée du realm : **doit correspondre** à la clé publique de `kong/kong.yml` (voir « Clé de signature du realm ») |
| Secret | `GATEWAY_SECRET` | secret partagé Kong → backend (`openssl rand -hex 24`) |
| Variable *(optionnelle)* | `DEPLOY_PORT` | port SSH (défaut `22`) |
| Variable *(optionnelle)* | `DEPLOY_PATH` | dossier de la stack sur le serveur (défaut `/opt/petites-annonces`) |
| Variable *(optionnelle)* | `POSTGRES_USER`, `KEYCLOAK_DB_USER`, `ANNONCES_DB_USER`, `KC_BOOTSTRAP_ADMIN_USERNAME` | défauts : `postgres`, `keycloak`, `annonces`, `admin` |

`GITHUB_TOKEN` est fourni automatiquement (publication et lecture des images GHCR). Les mots de passe des bases et des
comptes du realm ne sont appliqués qu'au **premier** démarrage (volume vide, import du realm) : pour les changer ensuite,
le faire aussi dans PostgreSQL et Keycloak, ou réinitialiser avec `docker compose down -v` sur le serveur.

## Arborescence

```
frontend/                       Angular 19 + Nginx (Dockerfile multi-stage)
backend/                        Spring Boot 3.5, Java 21 (Dockerfile multi-stage)
kong/kong.yml                   configuration déclarative de Kong (DB-less)
kong/plugins/identity-headers/  plugin Lua maison (handler.lua, schema.lua)
keycloak/realm-annonces.json    realm importé au démarrage
keycloak/realm-public-key.pem   clé publique de la paire RSA fixe du realm
postgres/init.sql               création des bases keycloak et annonces
scripts/                        get-token, forge-jwt, test-gateway (PowerShell + curl)
docker-compose.yml              5 services, réseaux public / internal
docker-compose.prod.yml         surcharge de production (images GHCR, ports sur 127.0.0.1)
.github/workflows/ci-cd.yml     CI (tests, images) et déploiement SSH
.env.example                    variables (valeurs de démo) à copier en .env
```
