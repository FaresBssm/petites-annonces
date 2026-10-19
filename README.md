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
.env.example                    variables (valeurs de démo) à copier en .env
```
