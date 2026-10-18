# PetitesAnnonces

Application d'annonces dont toute la sécurité est centralisée sur une API Gateway :
**Keycloak** (IdP, Authorization Code + PKCE) → **Kong OSS DB-less** (validation JWT, headers d'identité, rate limiting)
→ **Spring Boot 3** (contexte Spring Security reconstruit depuis les headers) → **PostgreSQL 16**,
avec un frontend **Angular** servi par Nginx. Énoncé complet : [`docs/enonce.pdf`](docs/enonce.pdf).

> 🚧 Projet en cours de construction, étape par étape (voir `CLAUDE.md`).

## Démarrage

```bash
cp .env.example .env
docker compose up -d --build
```

| Service | URL |
|---|---|
| Frontend | http://localhost:4200 |
| API Gateway (Kong) | http://localhost:8080/api |
| Keycloak | http://localhost:8180 |

## Comptes de test (realm `annonces`)

Mots de passe définis dans `.env` (valeurs de démo dans `.env.example`).

| Utilisateur | Rôle(s) effectif(s) | Équivalent énoncé |
|---|---|---|
| `fares` | USER | alice |
| `juba` | USER | bob |
| `mahdi` | MODERATEUR, USER | modo |
| `admin` | ADMIN, MODERATEUR, USER | admin |

Console d'administration Keycloak : http://localhost:8180 (compte `KC_BOOTSTRAP_ADMIN_*` du `.env`).

Obtenir un access token en ligne de commande (rejoue le flux Authorization Code + PKCE, le password grant étant interdit) :

```powershell
.\scripts\get-token.ps1 -User fares          # affiche le token
.\scripts\get-token.ps1 -User mahdi -Show    # + header et payload décodés
```

## Clé de signature du realm

Le plugin `jwt` de Kong OSS ne lit pas le JWKS : la clé publique du realm est **figée** dans `kong/kong.yml`.
Le realm embarque donc une **paire RSA fixe** (composant `rsa-fixe-annonces` de `realm-annonces.json`) dont la clé privée
est injectée à l'import depuis `REALM_RSA_PRIVATE_KEY` (`.env`). Sans cela, Keycloak générerait une nouvelle paire à chaque
création du realm et Kong rejetterait tous les tokens.

### Extraire la clé publique du realm (format PEM)

Depuis le realm démarré : l'endpoint public `http://localhost:8180/realms/annonces` renvoie la clé en base64 (`public_key`) ;
il suffit de l'encadrer des en-têtes PEM, en lignes de 64 caractères.

```powershell
$k = (Invoke-RestMethod http://localhost:8180/realms/annonces).public_key
"-----BEGIN PUBLIC KEY-----`n" + (($k -split '(.{64})' | ? { $_ }) -join "`n") + "`n-----END PUBLIC KEY-----"
```

Autres moyens : console Keycloak → realm *annonces* → *Realm settings* → *Keys* → RS256 → *Public key* ;
ou hors ligne depuis la clé privée : `openssl pkey -in private.pem -pubout`.
Le résultat est versionné dans `keycloak/realm-public-key.pem` puis recopié dans `kong/kong.yml`.

### Générer une nouvelle paire (si besoin)

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem
openssl pkey -in private.pem -pubout -out keycloak/realm-public-key.pem
grep -v -- ----- private.pem | tr -d '\n'     # -> valeur de REALM_RSA_PRIVATE_KEY (une ligne)
```

Puis mettre à jour `kong/kong.yml` et réimporter le realm (`docker compose down -v`).

## Arborescence

```
frontend/                       Angular + Nginx
backend/                        Spring Boot 3 (Java 21)
kong/kong.yml                   configuration déclarative Kong (DB-less)
kong/plugins/identity-headers/  plugin Lua maison (handler.lua, schema.lua)
keycloak/realm-annonces.json    realm importé au démarrage
postgres/init.sql               création des bases keycloak et annonces
scripts/                        scripts de test (gateway, tokens)
docs/                           énoncé
```

_Le README sera complété au fil des étapes : comptes de test, schéma d'architecture, choix techniques,
procédure d'extraction de la clé publique du realm._
