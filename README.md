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
