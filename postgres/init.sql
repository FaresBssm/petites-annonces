-- Exécuté une seule fois par l'image postgres, au premier démarrage (volume vide).
-- Les mots de passe ne sont pas écrits ici : \getenv (psql 15+) les lit dans les
-- variables d'environnement du conteneur, alimentées par le fichier .env.

\getenv keycloak_user     KEYCLOAK_DB_USER
\getenv keycloak_password KEYCLOAK_DB_PASSWORD
\getenv annonces_user     ANNONCES_DB_USER
\getenv annonces_password ANNONCES_DB_PASSWORD

-- Base de Keycloak
CREATE ROLE :"keycloak_user" WITH LOGIN PASSWORD :'keycloak_password';
CREATE DATABASE keycloak OWNER :"keycloak_user";

-- Base applicative (schéma géré par Flyway côté backend)
CREATE ROLE :"annonces_user" WITH LOGIN PASSWORD :'annonces_password';
CREATE DATABASE annonces OWNER :"annonces_user";

-- Chaque service n'accède qu'à sa propre base
REVOKE ALL ON DATABASE keycloak FROM PUBLIC;
REVOKE ALL ON DATABASE annonces FROM PUBLIC;
